package com.placify.placement.service;

import com.placify.common.messaging.EventPublisher;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.CalendarEvent;
import com.placify.placement.domain.InterviewSlot;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Offer;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.CalendarEventRepository;
import com.placify.placement.repository.InterviewSlotRepository;
import com.placify.placement.repository.JobRepository;
import com.placify.placement.repository.OfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Background jobs: offer deadline reminders and expiry, interview and event
 * reminders, and closing jobs whose deadline passed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlacementScheduler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

    private final OfferRepository offers;
    private final InterviewSlotRepository slots;
    private final CalendarEventRepository events;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final EventPublisher publisher;

    @Value("${placify.placement.offer-reminder-hours:24}")
    private int offerReminderHours;

    @Value("${placify.placement.slot-reminder-minutes:60}")
    private int slotReminderMinutes;

    /** Every 10 minutes: remind about offers close to their deadline and expire old ones. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    @Transactional
    public void offers() {
        LocalDateTime now = LocalDateTime.now();
        for (Offer o : offers.findByStatusAndReminderSentFalseAndResponseDeadlineBetween(Offer.Status.ISSUED, now,
                now.plusHours(offerReminderHours))) {
            o.setReminderSent(true);
            publisher.notifyUser(o.getStudentUserId(), "Offer deadline approaching",
                    "Respond to the offer from " + o.getCompanyName() + " before " + FMT.format(o.getResponseDeadline()) + ".",
                    "OFFER", "/student/offers", true);
        }
        for (Offer o : offers.findByStatusAndResponseDeadlineBefore(Offer.Status.ISSUED, now)) {
            o.setStatus(Offer.Status.EXPIRED);
            publisher.notifyUser(o.getStudentUserId(), "Offer expired",
                    "The offer from " + o.getCompanyName() + " expired because it was not accepted in time.",
                    "OFFER", "/student/offers", true);
        }
    }

    /** Every 5 minutes: remind students about interviews starting soon. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    @Transactional
    public void interviewReminders() {
        LocalDateTime now = LocalDateTime.now();
        for (InterviewSlot s : slots.findByReminderSentFalseAndBookedByIsNotNullAndStartTimeBetween(now,
                now.plusMinutes(slotReminderMinutes))) {
            s.setReminderSent(true);
            String company = jobs.findById(s.getJobId()).map(Job::getCompanyName).orElse("Company");
            publisher.notifyUser(s.getBookedBy(), "Interview soon: " + company,
                    "Your interview starts at " + s.getStartTime().toLocalTime()
                            + (s.getLocation() == null ? "" : " (" + s.getLocation() + ")") + ". Be ready!",
                    "REMINDER", "/student/slots", true);
        }
    }

    /** Every 30 minutes: remind applicants about job events in the next 24 hours. */
    @Scheduled(fixedDelay = 1_800_000, initialDelay = 120_000)
    @Transactional
    public void eventReminders() {
        LocalDateTime now = LocalDateTime.now();
        for (CalendarEvent e : events.findByReminderSentFalseAndStartTimeBetween(now, now.plusHours(24))) {
            e.setReminderSent(true);
            if (e.getJobId() == null) {
                continue;
            }
            List<String> ids = applications.findByJobId(e.getJobId()).stream()
                    .filter(a -> a.getStatus().isActive())
                    .map(Application::getStudentUserId).toList();
            publisher.notifyUsers(ids, "Tomorrow: " + e.getTitle(),
                    e.getTitle() + " at " + FMT.format(e.getStartTime()) + (e.getLocation() == null ? "" : ", " + e.getLocation()),
                    "REMINDER", "/student/calendar", true);
        }
    }

    /** Hourly: close jobs after their deadline once every applicant is decided. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void closeFinishedJobs() {
        LocalDateTime now = LocalDateTime.now();
        for (Job job : jobs.findByStatusOrderByPublishedAtDesc(Job.Status.OPEN)) {
            if (job.getApplicationDeadline() == null || job.getApplicationDeadline().isAfter(now.minusDays(30))) {
                continue;
            }
            boolean anyActive = applications.findByJobId(job.getId()).stream().anyMatch(a -> a.getStatus().isActive());
            if (!anyActive) {
                job.setStatus(Job.Status.CLOSED);
                log.info("Auto-closed job {} ({})", job.getId(), job.getTitle());
            }
        }
    }
}

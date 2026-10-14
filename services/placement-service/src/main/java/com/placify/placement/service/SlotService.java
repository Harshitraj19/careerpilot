package com.placify.placement.service;

import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.InterviewSlot;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Round;
import com.placify.placement.domain.RoundResult;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.InterviewSlotRepository;
import com.placify.placement.repository.JobRepository;
import com.placify.placement.repository.RoundRepository;
import com.placify.placement.repository.RoundResultRepository;
import com.placify.placement.web.PlacementDtos.SlotGenerateRequest;
import com.placify.placement.web.PlacementDtos.SlotView;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interview slot booking with clash detection: a student can never hold two
 * slots (of any companies) that overlap in time.
 */
@Service
@RequiredArgsConstructor
public class SlotService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

    private final InterviewSlotRepository slots;
    private final RoundRepository rounds;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final RoundResultRepository results;
    private final JobService jobService;
    private final EventPublisher publisher;

    /** Splits a time window into equal slots, e.g. 10:00-13:00 in 30-minute slots. */
    @Transactional
    public List<SlotView> generate(Long jobId, SlotGenerateRequest req) {
        Job job = jobService.requireOwnerOrStaff(jobId);
        Round round = rounds.findById(req.roundId()).orElseThrow(() -> ApiException.notFound("Round not found"));
        if (!round.getJobId().equals(jobId)) {
            throw ApiException.badRequest("Round does not belong to this job");
        }
        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("End time must be after start time");
        }
        List<InterviewSlot> created = new ArrayList<>();
        LocalDateTime cursor = req.startTime();
        while (!cursor.plusMinutes(req.durationMinutes()).isAfter(req.endTime()) && created.size() < 200) {
            created.add(slots.save(InterviewSlot.builder()
                    .jobId(jobId)
                    .roundId(round.getId())
                    .companyUserId(job.getCompanyUserId())
                    .startTime(cursor)
                    .endTime(cursor.plusMinutes(req.durationMinutes()))
                    .location(req.location())
                    .build()));
            cursor = cursor.plusMinutes(req.durationMinutes() + req.gapMinutes());
        }
        if (created.isEmpty()) {
            throw ApiException.badRequest("The window is shorter than one slot");
        }
        // tell candidates waiting in this round that they can book
        List<String> waiting = results.findByRoundIdAndStatusIn(round.getId(), EnumSet.of(RoundResult.Status.PENDING))
                .stream().map(RoundResult::getStudentUserId).toList();
        publisher.notifyUsers(waiting, "Book your interview slot",
                job.getCompanyName() + " published slots for " + round.getName() + ". Book one before they fill up.",
                "SLOTS", "/student/slots", true);
        return created.stream().map(s -> view(s, job, round, null)).toList();
    }

    /** Company/TPO: all slots; student: slots of rounds they are waiting in. */
    public List<SlotView> forJob(Long jobId) {
        UserPrincipal me = CurrentUser.get();
        Job job = jobService.get(jobId);
        Map<Long, Round> roundMap = rounds.findByJobIdOrderBySequenceAsc(jobId).stream()
                .collect(Collectors.toMap(Round::getId, Function.identity()));
        if (me.role() == Role.STUDENT) {
            Application app = applications.findByJobIdAndStudentUserId(jobId, me.userId())
                    .orElseThrow(() -> ApiException.forbidden("You have not applied to this job"));
            if (app.getCurrentRoundId() == null) {
                return List.of();
            }
            return slots.findByJobIdAndRoundIdOrderByStartTimeAsc(jobId, app.getCurrentRoundId()).stream()
                    .filter(s -> !s.isBooked() || me.userId().equals(s.getBookedBy()))
                    .filter(s -> s.getStartTime().isAfter(LocalDateTime.now()) || me.userId().equals(s.getBookedBy()))
                    .map(s -> view(s, job, roundMap.get(s.getRoundId()), me.userId()))
                    .toList();
        }
        jobService.requireOwnerOrStaff(jobId);
        Map<String, String> names = applications.findByJobId(jobId).stream()
                .collect(Collectors.toMap(Application::getStudentUserId, a -> a.getStudentName() + " (" + a.getRegNo() + ")", (a, b) -> a));
        return slots.findByJobIdOrderByStartTimeAsc(jobId).stream()
                .map(s -> {
                    SlotView v = view(s, job, roundMap.get(s.getRoundId()), null);
                    return new SlotView(v.id(), v.jobId(), v.roundId(), v.roundName(), v.companyName(), v.jobTitle(),
                            v.startTime(), v.endTime(), v.location(), v.booked(), false,
                            s.isBooked() ? names.get(s.getBookedBy()) : null);
                })
                .toList();
    }

    public List<SlotView> mine() {
        String me = CurrentUser.id();
        List<InterviewSlot> list = slots.findByBookedByOrderByStartTimeAsc(me);
        Map<Long, Job> jobMap = jobs.findByIdIn(list.stream().map(InterviewSlot::getJobId).distinct().toList())
                .stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, Round> roundMap = rounds.findByJobIdIn(jobMap.keySet()).stream()
                .collect(Collectors.toMap(Round::getId, Function.identity()));
        return list.stream().map(s -> view(s, jobMap.get(s.getJobId()), roundMap.get(s.getRoundId()), me)).toList();
    }

    @Transactional
    public SlotView book(Long slotId) {
        String me = CurrentUser.id();
        InterviewSlot slot = slots.findById(slotId).orElseThrow(() -> ApiException.notFound("Slot not found"));
        if (slot.isBooked()) {
            throw ApiException.conflict("This slot was just taken. Please choose another one.");
        }
        if (!slot.getStartTime().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("This slot has already started");
        }
        Application app = applications.findByJobIdAndStudentUserId(slot.getJobId(), me)
                .orElseThrow(() -> ApiException.forbidden("You have not applied to this job"));
        if (!slot.getRoundId().equals(app.getCurrentRoundId())) {
            throw ApiException.forbidden("You are not in this round");
        }
        if (slots.existsByJobIdAndRoundIdAndBookedBy(slot.getJobId(), slot.getRoundId(), me)) {
            throw ApiException.conflict("You already booked a slot for this round. Cancel it first to change.");
        }
        // ---- clash detection across all companies
        List<InterviewSlot> clashes = slots.findOverlapping(me, slot.getStartTime(), slot.getEndTime());
        if (!clashes.isEmpty()) {
            InterviewSlot c = clashes.get(0);
            String company = jobs.findById(c.getJobId()).map(Job::getCompanyName).orElse("another company");
            throw ApiException.conflict("Time clash: you already have an interview with " + company + " at "
                    + FMT.format(c.getStartTime()) + " - " + c.getEndTime().toLocalTime());
        }
        slot.setBookedBy(me);
        slot.setBookedAt(LocalDateTime.now());
        try {
            slots.saveAndFlush(slot);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw ApiException.conflict("This slot was just taken. Please choose another one.");
        }
        Job job = jobService.get(slot.getJobId());
        Round round = rounds.findById(slot.getRoundId()).orElse(null);
        publisher.notifyUser(me, "Interview slot booked",
                job.getCompanyName() + " - " + (round == null ? "Interview" : round.getName()) + " on "
                        + FMT.format(slot.getStartTime()) + (slot.getLocation() == null ? "" : " at " + slot.getLocation()),
                "SLOTS", "/student/slots", true);
        return view(slot, job, round, me);
    }

    @Transactional
    public void cancel(Long slotId) {
        String me = CurrentUser.id();
        InterviewSlot slot = slots.findById(slotId).orElseThrow(() -> ApiException.notFound("Slot not found"));
        if (!me.equals(slot.getBookedBy())) {
            throw ApiException.forbidden("Not your booking");
        }
        if (slot.getStartTime().minusHours(2).isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Bookings can only be cancelled up to 2 hours before the slot");
        }
        slot.setBookedBy(null);
        slot.setBookedAt(null);
        slot.setReminderSent(false);
    }

    @Transactional
    public void delete(Long slotId) {
        InterviewSlot slot = slots.findById(slotId).orElseThrow(() -> ApiException.notFound("Slot not found"));
        jobService.requireOwnerOrStaff(slot.getJobId());
        if (slot.isBooked()) {
            publisher.notifyUser(slot.getBookedBy(), "Interview slot cancelled",
                    "Your slot on " + FMT.format(slot.getStartTime()) + " was cancelled by the company. Please book another.",
                    "SLOTS", "/student/slots", true);
        }
        slots.delete(slot);
    }

    private SlotView view(InterviewSlot s, Job job, Round round, String viewer) {
        return new SlotView(s.getId(), s.getJobId(), s.getRoundId(), round == null ? null : round.getName(),
                job == null ? null : job.getCompanyName(), job == null ? null : job.getTitle(),
                s.getStartTime(), s.getEndTime(), s.getLocation(), s.isBooked(),
                viewer != null && viewer.equals(s.getBookedBy()), null);
    }
}

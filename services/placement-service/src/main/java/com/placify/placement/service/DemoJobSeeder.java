package com.placify.placement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.common.demo.DemoData;
import com.placify.placement.domain.CalendarEvent;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Round;
import com.placify.placement.repository.CalendarEventRepository;
import com.placify.placement.repository.JobRepository;
import com.placify.placement.repository.RoundRepository;
import com.placify.placement.web.PlacementDtos.SalaryComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Demo placement season (DEMO_DATA=true): 17 drives of 12 companies from {@code demo/drives.json}
 * with salary breakdowns and selection rounds, plus a calendar of pre-placement talks, tests,
 * interviews and placement-office workshops. Dates are relative to the day the data is created.
 * Missing drives and events are added to existing databases; existing records are not changed
 * (except that a missing salary breakdown is filled in).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = DemoData.PROPERTY, havingValue = "true")
public class DemoJobSeeder implements ApplicationRunner {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JobRepository jobs;
    private final RoundRepository rounds;
    private final CalendarEventRepository events;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    record DemoRound(String name, Round.Type type, int day, int hour, BigDecimal cutoff, String description) {
    }

    record DemoEvent(String title, CalendarEvent.Type type, int day, int hour, int hours, String location, String description) {
    }

    record Drive(String companyId, String title, Job.Type jobType, BigDecimal ctcLpa, Integer stipend, Job.Tier tier,
                 String location, String skills, BigDecimal minCgpa, String branches, Integer maxBacklogs, int deadlineDays,
                 String description, List<SalaryComponent> salary, List<DemoRound> rounds, List<DemoEvent> events) {
    }

    /** Placement-office events that are not tied to a company. */
    private static final List<DemoEvent> OFFICE_EVENTS = List.of(
            new DemoEvent("Placement orientation for final-year students", CalendarEvent.Type.OTHER, 0, 16, 1, "Main auditorium",
                    "How the placement season works: policies, dream/super-dream tiers, eligibility and the drive calendar."),
            new DemoEvent("Resume writing workshop", CalendarEvent.Type.WORKSHOP, 1, 14, 2, "Seminar hall 1",
                    "Build an ATS-friendly one-page resume. Bring your laptop and current resume."),
            new DemoEvent("Aptitude bootcamp", CalendarEvent.Type.WORKSHOP, 5, 10, 3, "Computer centre lab 2",
                    "Timed practice for quantitative, logical and verbal sections with solutions."),
            new DemoEvent("Mock interview day", CalendarEvent.Type.WORKSHOP, 7, 9, 6, "Placement cell, block A",
                    "One-to-one mock technical and HR interviews with alumni volunteers. Book a slot with the placement office."),
            new DemoEvent("Group discussion practice", CalendarEvent.Type.WORKSHOP, 16, 14, 2, "Seminar hall 2",
                    "Practise structured group discussions on current tech and business topics."));

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        List<Drive> drives;
        try (InputStream in = new ClassPathResource("demo/drives.json").getInputStream()) {
            drives = mapper.readValue(in, new TypeReference<>() {
            });
        }
        Map<String, String> companyNames = DemoData.COMPANIES.stream()
                .collect(Collectors.toMap(DemoData.Company::id, DemoData.Company::name));
        LocalDate today = LocalDate.now();
        int added = 0;
        for (Drive d : drives) {
            String company = companyNames.get(d.companyId());
            Job existing = jobs.findByCompanyUserIdOrderByCreatedAtDesc(d.companyId()).stream()
                    .filter(j -> j.getTitle().equalsIgnoreCase(d.title())).findFirst().orElse(null);
            if (existing != null) {
                if (existing.getCtcBreakdown() == null) {
                    existing.setCtcBreakdown(JobService.writeBreakdown(d.salary()));
                }
                if (events.findByJobIdOrderByStartTimeAsc(existing.getId()).isEmpty()) {
                    addEvents(existing, d, rounds.findByJobIdOrderBySequenceAsc(existing.getId()), today);
                }
                continue;
            }
            Job job = jobs.save(Job.builder().companyUserId(d.companyId()).companyName(company).title(d.title())
                    .description(d.description()).requiredSkills(d.skills()).location(d.location()).jobType(d.jobType())
                    .ctcLpa(d.ctcLpa()).stipend(d.stipend()).ctcBreakdown(JobService.writeBreakdown(d.salary()))
                    .tier(d.tier()).status(Job.Status.OPEN).minCgpa(d.minCgpa()).allowedBranches(d.branches())
                    .maxBacklogs(d.maxBacklogs()).eligibleBatch(DemoData.BATCH)
                    .applicationDeadline(at(today, d.deadlineDays(), 23).withMinute(59)).publishedAt(LocalDateTime.now()).build());
            int seq = 1;
            for (DemoRound r : d.rounds()) {
                rounds.save(Round.builder().jobId(job.getId()).sequence(seq++).name(r.name()).type(r.type())
                        .cutoffPercent(r.cutoff()).description(r.description()).scheduledAt(at(today, r.day(), r.hour())).build());
            }
            addEvents(job, d, rounds.findByJobIdOrderBySequenceAsc(job.getId()), today);
            added++;
        }
        Map<String, CalendarEvent> byTitle = events.findAll().stream()
                .collect(Collectors.toMap(CalendarEvent::getTitle, Function.identity(), (a, b) -> a));
        for (DemoEvent e : OFFICE_EVENTS) {
            if (!byTitle.containsKey(e.title())) {
                events.save(event(e, null, null, today));
            }
        }
        if (added > 0) {
            log.info("Added {} demo drives with rounds and calendar events", added);
        }
    }

    /** The company's own events (talks, drive days) plus one calendar entry per dated round. */
    private void addEvents(Job job, Drive d, List<Round> jobRounds, LocalDate today) {
        for (DemoEvent e : d.events()) {
            events.save(event(e, job.getId(), job.getCompanyName(), today));
        }
        for (Round r : jobRounds) {
            if (r.getScheduledAt() == null) {
                continue;
            }
            CalendarEvent.Type type = r.getType().isTest() ? CalendarEvent.Type.TEST : CalendarEvent.Type.INTERVIEW;
            int hours = r.getType().isTest() ? 1 : 6;
            events.save(CalendarEvent.builder()
                    .title(job.getCompanyName() + " - " + r.getName())
                    .type(type)
                    .description(job.getTitle() + ": " + (r.getDescription() == null ? r.getName() : r.getDescription()))
                    .jobId(job.getId()).companyName(job.getCompanyName())
                    .startTime(r.getScheduledAt()).endTime(r.getScheduledAt().plusHours(hours))
                    .location(r.getType().isTest() ? "Online (proctored)" : "Placement cell, block A")
                    .createdBy(DemoData.TPO_ID).build());
        }
    }

    private CalendarEvent event(DemoEvent e, Long jobId, String company, LocalDate today) {
        LocalDateTime start = at(today, e.day(), e.hour());
        return CalendarEvent.builder().title(e.title()).type(e.type()).description(e.description())
                .jobId(jobId).companyName(company).startTime(start).endTime(start.plusHours(Math.max(1, e.hours())))
                .location(e.location()).attendanceCode(code()).createdBy(DemoData.TPO_ID).build();
    }

    private static LocalDateTime at(LocalDate today, int days, int hour) {
        return today.plusDays(days).atTime(hour, 0);
    }

    private String code() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}

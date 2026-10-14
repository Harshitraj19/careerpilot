package com.placify.placement.service;

import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.CalendarEvent;
import com.placify.placement.domain.EventAttendance;
import com.placify.placement.domain.Job;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.CalendarEventRepository;
import com.placify.placement.repository.EventAttendanceRepository;
import com.placify.placement.web.PlacementDtos.EventRequest;
import com.placify.placement.web.PlacementDtos.EventView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Placement calendar (drives, pre-placement talks, tests, interviews) and PPT attendance.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final CalendarEventRepository events;
    private final EventAttendanceRepository attendance;
    private final ApplicationRepository applications;
    private final JobService jobService;
    private final EventPublisher publisher;

    public List<EventView> list(LocalDateTime from, LocalDateTime to) {
        UserPrincipal me = CurrentUser.get();
        return events.findByStartTimeBetweenOrderByStartTimeAsc(from, to).stream().map(e -> view(e, me)).toList();
    }

    public EventView view(CalendarEvent e, UserPrincipal me) {
        boolean canEdit = canEdit(e, me);
        Boolean attended = me.role() == Role.STUDENT ? attendance.existsByEventIdAndStudentUserId(e.getId(), me.userId()) : null;
        return new EventView(e.getId(), e.getTitle(), e.getType(), e.getDescription(), e.getJobId(), e.getCompanyName(),
                e.getStartTime(), e.getEndTime(), e.getLocation(), canEdit ? e.getAttendanceCode() : null, attended, canEdit);
    }

    @Transactional
    public CalendarEvent create(EventRequest req) {
        validateTimes(req);
        UserPrincipal me = CurrentUser.get();
        String companyName = null;
        if (req.jobId() != null) {
            Job job = jobService.requireOwnerOrStaff(req.jobId());
            companyName = job.getCompanyName();
        } else if (me.role() == Role.COMPANY) {
            throw ApiException.badRequest("Companies must link events to one of their jobs");
        }
        CalendarEvent e = events.save(CalendarEvent.builder()
                .title(req.title().trim())
                .type(req.type())
                .description(req.description())
                .jobId(req.jobId())
                .companyName(companyName)
                .startTime(req.startTime())
                .endTime(req.endTime())
                .location(req.location())
                .attendanceCode(req.generateAttendanceCode() || req.type() == CalendarEvent.Type.PRE_PLACEMENT_TALK ? code() : null)
                .createdBy(me.userId())
                .build());
        notifyAudience(e, "New event: " + e.getTitle());
        return e;
    }

    @Transactional
    public CalendarEvent update(Long id, EventRequest req) {
        validateTimes(req);
        CalendarEvent e = editable(id);
        e.setTitle(req.title().trim());
        e.setType(req.type());
        e.setDescription(req.description());
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setLocation(req.location());
        e.setReminderSent(false);
        if (req.generateAttendanceCode() && e.getAttendanceCode() == null) {
            e.setAttendanceCode(code());
        }
        notifyAudience(e, "Event updated: " + e.getTitle());
        return e;
    }

    @Transactional
    public void delete(Long id) {
        events.delete(editable(id));
    }

    /** Staff/company marks attendance for a list of students. */
    @Transactional
    public int markAttendance(Long eventId, List<String> studentUserIds) {
        CalendarEvent e = editable(eventId);
        String me = CurrentUser.id();
        int added = 0;
        for (String studentId : studentUserIds) {
            if (!attendance.existsByEventIdAndStudentUserId(e.getId(), studentId)) {
                attendance.save(EventAttendance.builder().eventId(e.getId()).studentUserId(studentId).markedBy(me).build());
                added++;
            }
        }
        return added;
    }

    /** Student checks in with the code shown at the talk (valid from 15 min before start until the end). */
    @Transactional
    public void checkIn(Long eventId, String code) {
        CalendarEvent e = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (e.getAttendanceCode() == null || !e.getAttendanceCode().equalsIgnoreCase(code.trim())) {
            throw ApiException.badRequest("Invalid attendance code");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(e.getStartTime().minusMinutes(15)) || now.isAfter(e.getEndTime())) {
            throw ApiException.badRequest("Check-in is only open during the event");
        }
        String studentId = CurrentUser.id();
        if (!attendance.existsByEventIdAndStudentUserId(eventId, studentId)) {
            attendance.save(EventAttendance.builder().eventId(eventId).studentUserId(studentId).markedBy("SELF").build());
        }
    }

    public List<EventAttendance> attendance(Long eventId) {
        editable(eventId);
        return attendance.findByEventId(eventId);
    }

    private CalendarEvent editable(Long id) {
        CalendarEvent e = events.findById(id).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (!canEdit(e, CurrentUser.get())) {
            throw ApiException.forbidden("You cannot change this event");
        }
        return e;
    }

    private boolean canEdit(CalendarEvent e, UserPrincipal me) {
        return me.isStaff() || e.getCreatedBy().equals(me.userId());
    }

    private void validateTimes(EventRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("End time must be after start time");
        }
    }

    /** Applicants of the linked job are informed; general events are visible in the calendar. */
    private void notifyAudience(CalendarEvent e, String title) {
        if (e.getJobId() == null) {
            return;
        }
        List<String> ids = applications.findByJobId(e.getJobId()).stream()
                .filter(a -> a.getStatus().isActive())
                .map(Application::getStudentUserId)
                .toList();
        publisher.notifyUsers(ids, title, e.getTitle() + " on " + e.getStartTime().toLocalDate() + " at "
                + e.getStartTime().toLocalTime() + (e.getLocation() == null ? "" : ", " + e.getLocation()), "EVENT", "/student/calendar", false);
    }

    private String code() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}

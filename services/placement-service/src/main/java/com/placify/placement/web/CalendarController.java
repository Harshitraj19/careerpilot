package com.placify.placement.web;

import com.placify.common.security.CurrentUser;
import com.placify.placement.domain.EventAttendance;
import com.placify.placement.service.CalendarService;
import com.placify.placement.web.PlacementDtos.AttendanceRequest;
import com.placify.placement.web.PlacementDtos.CheckInRequest;
import com.placify.placement.web.PlacementDtos.EventRequest;
import com.placify.placement.web.PlacementDtos.EventView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Placement calendar")
@RestController
@RequestMapping("/api/placement/events")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService service;

    @GetMapping
    public List<EventView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(30) : from;
        LocalDateTime end = to == null ? start.plusDays(120) : to;
        return service.list(start, end);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public EventView create(@Valid @RequestBody EventRequest req) {
        return service.view(service.create(req), CurrentUser.get());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public EventView update(@PathVariable Long id, @Valid @RequestBody EventRequest req) {
        return service.view(service.update(id, req), CurrentUser.get());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @Operation(summary = "Mark students as present (pre-placement talk attendance)")
    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public Map<String, Integer> mark(@PathVariable Long id, @Valid @RequestBody AttendanceRequest req) {
        return Map.of("added", service.markAttendance(id, req.studentUserIds()));
    }

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public List<EventAttendance> attendance(@PathVariable Long id) {
        return service.attendance(id);
    }

    @Operation(summary = "Student self check-in with the code shown during the talk")
    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, String> checkIn(@PathVariable Long id, @Valid @RequestBody CheckInRequest req) {
        service.checkIn(id, req.code());
        return Map.of("message", "Attendance recorded");
    }
}

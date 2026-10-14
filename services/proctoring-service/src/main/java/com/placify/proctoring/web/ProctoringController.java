package com.placify.proctoring.web;

import com.placify.proctoring.service.ProctoringService;
import com.placify.proctoring.web.ProctoringDtos.BrowserEvent;
import com.placify.proctoring.web.ProctoringDtos.FrameRequest;
import com.placify.proctoring.web.ProctoringDtos.Report;
import com.placify.proctoring.web.ProctoringDtos.SessionStatus;
import com.placify.proctoring.web.ProctoringDtos.StartRequest;
import com.placify.proctoring.web.ProctoringDtos.Summary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Proctoring")
@RestController
@RequiredArgsConstructor
public class ProctoringController {

    private final ProctoringService service;

    @Operation(summary = "Start proctoring for an attempt: consent + start photo (identity check)")
    @PostMapping("/api/proctoring/sessions")
    @PreAuthorize("hasRole('STUDENT')")
    public SessionStatus start(@Valid @RequestBody StartRequest req) {
        return service.start(req);
    }

    @Operation(summary = "Send a webcam frame (every 2-3 s). Analysed asynchronously; returns live status.")
    @PostMapping("/api/proctoring/sessions/{id}/frames")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SessionStatus> frame(@PathVariable Long id, @Valid @RequestBody FrameRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.submitFrame(id, req.image()));
    }

    @Operation(summary = "Report a browser event: TAB_SWITCH, FULLSCREEN_EXIT, COPY_PASTE, WINDOW_BLUR, NOISE")
    @PostMapping("/api/proctoring/sessions/{id}/events")
    @PreAuthorize("hasRole('STUDENT')")
    public SessionStatus event(@PathVariable Long id, @Valid @RequestBody BrowserEvent event) {
        return service.browserEvent(id, event);
    }

    @GetMapping("/api/proctoring/sessions/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public SessionStatus status(@PathVariable Long id) {
        return service.currentStatus(id);
    }

    @PostMapping("/api/proctoring/sessions/{id}/end")
    @PreAuthorize("hasRole('STUDENT')")
    public SessionStatus end(@PathVariable Long id) {
        return service.end(id);
    }

    @Operation(summary = "Proctoring report: trust score, violation timeline, identity result")
    @GetMapping("/api/proctoring/reports/attempts/{attemptId}")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public Report report(@PathVariable Long attemptId) {
        return service.report(attemptId);
    }

    @GetMapping("/api/proctoring/reports/violations/{id}/snapshot")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public ResponseEntity<byte[]> snapshot(@PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_JPEG)
                .body(service.snapshot(id));
    }

    @GetMapping("/internal/proctoring/attempts/{attemptId}/summary")
    public Summary summary(@PathVariable Long attemptId) {
        return service.summary(attemptId);
    }
}

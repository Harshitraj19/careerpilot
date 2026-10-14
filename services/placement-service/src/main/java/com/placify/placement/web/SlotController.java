package com.placify.placement.web;

import com.placify.placement.service.SlotService;
import com.placify.placement.web.PlacementDtos.SlotGenerateRequest;
import com.placify.placement.web.PlacementDtos.SlotView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Interview slots")
@RestController
@RequestMapping("/api/placement")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService service;

    @Operation(summary = "Create interview slots by splitting a time window")
    @PostMapping("/jobs/{jobId}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public List<SlotView> generate(@PathVariable Long jobId, @Valid @RequestBody SlotGenerateRequest req) {
        return service.generate(jobId, req);
    }

    @GetMapping("/jobs/{jobId}/slots")
    public List<SlotView> forJob(@PathVariable Long jobId) {
        return service.forJob(jobId);
    }

    @GetMapping("/slots/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<SlotView> mine() {
        return service.mine();
    }

    @Operation(summary = "Book a slot (blocked if it clashes with another booked interview)")
    @PostMapping("/slots/{id}/book")
    @PreAuthorize("hasRole('STUDENT')")
    public SlotView book(@PathVariable Long id) {
        return service.book(id);
    }

    @PostMapping("/slots/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public void cancel(@PathVariable Long id) {
        service.cancel(id);
    }

    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

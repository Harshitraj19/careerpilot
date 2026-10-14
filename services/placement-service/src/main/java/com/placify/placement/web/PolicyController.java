package com.placify.placement.web;

import com.placify.placement.domain.PlacementPolicy;
import com.placify.placement.service.PolicyService;
import com.placify.placement.web.PlacementDtos.PolicyUpdate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Placement policy")
@RestController
@RequestMapping("/api/placement/policy")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService service;

    @GetMapping
    public PlacementPolicy get() {
        return service.get();
    }

    @Operation(summary = "Configure tiers (Dream / Super-Dream), one-offer policy, application limit and PPT rule")
    @PutMapping
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public PlacementPolicy update(@Valid @RequestBody PolicyUpdate req) {
        return service.update(req);
    }
}

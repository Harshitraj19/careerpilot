package com.placify.placement.service;

import com.placify.common.audit.Audited;
import com.placify.common.security.CurrentUser;
import com.placify.common.web.ApiException;
import com.placify.placement.domain.PlacementPolicy;
import com.placify.placement.repository.PolicyRepository;
import com.placify.placement.web.PlacementDtos.PolicyUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the college placement policy.
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository repository;

    public PlacementPolicy get() {
        return repository.findById(1L).orElseThrow(() -> ApiException.notFound("Placement policy is not configured"));
    }

    @Audited(action = "POLICY_UPDATED")
    @Transactional
    public PlacementPolicy update(PolicyUpdate req) {
        if (req.superDreamMinCtc().compareTo(req.dreamMinCtc()) <= 0) {
            throw ApiException.badRequest("Super-Dream CTC must be higher than Dream CTC");
        }
        PlacementPolicy p = get();
        p.setDreamMinCtc(req.dreamMinCtc());
        p.setSuperDreamMinCtc(req.superDreamMinCtc());
        p.setOneOfferPolicy(req.oneOfferPolicy());
        p.setMaxActiveApplications(req.maxActiveApplications());
        p.setPptMandatory(req.pptMandatory());
        p.setResumeRequired(req.resumeRequired());
        p.setUpdatedBy(CurrentUser.id());
        return p;
    }
}

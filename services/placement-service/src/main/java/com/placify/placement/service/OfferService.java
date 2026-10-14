package com.placify.placement.service;

import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.storage.FileStorageService;
import com.placify.common.web.ApiException;
import com.placify.placement.client.PlacementClients;
import com.placify.placement.client.StudentInfo;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.Certificate;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Offer;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.CertificateRepository;
import com.placify.placement.repository.OfferRepository;
import com.placify.placement.web.PlacementDtos.OfferView;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Offer management: issue (company) -> accept/decline (student) -> verify (TPO)
 * -> certificate. Students can also report off-campus offers for verification.
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository offers;
    private final ApplicationRepository applications;
    private final CertificateRepository certificates;
    private final JobService jobService;
    private final PolicyService policyService;
    private final CertificateService certificateService;
    private final PlacementClients clients;
    private final FileStorageService storage;
    private final EventPublisher publisher;
    private final Tika tika = new Tika();

    // ---------------------------------------------------------------- company

    @Audited(action = "OFFER_ISSUED", target = "#applicationId")
    @Transactional
    public Offer issue(Long applicationId, String role, BigDecimal ctcLpa, LocalDateTime deadline, MultipartFile letter) {
        Application app = applications.findById(applicationId).orElseThrow(() -> ApiException.notFound("Application not found"));
        Job job = jobService.requireOwnerOrStaff(app.getJobId());
        if (app.getStatus() != Application.Status.SELECTED) {
            throw ApiException.badRequest("Offers can only be issued to selected candidates");
        }
        if (offers.findByApplicationId(applicationId).isPresent()) {
            throw ApiException.conflict("An offer was already issued for this application");
        }
        if (deadline == null || !deadline.isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Response deadline must be in the future");
        }
        BigDecimal ctc = ctcLpa == null ? job.getCtcLpa() : ctcLpa;
        Offer offer = offers.save(Offer.builder()
                .applicationId(applicationId)
                .jobId(job.getId())
                .studentUserId(app.getStudentUserId())
                .companyUserId(job.getCompanyUserId())
                .companyName(job.getCompanyName())
                .role(role == null || role.isBlank() ? job.getTitle() : role.trim())
                .ctcLpa(ctc)
                .tier(policyService.get().tierFor(ctc))
                .offerLetterPath(letter == null || letter.isEmpty() ? null : storeLetter(letter))
                .status(Offer.Status.ISSUED)
                .offCampus(false)
                .responseDeadline(deadline)
                .build());
        publisher.notifyUser(app.getStudentUserId(), "Offer from " + job.getCompanyName() + "!",
                "You have received an offer for " + offer.getRole() + (ctc == null ? "" : " (" + ctc + " LPA)")
                        + ". Accept or decline before " + deadline.toLocalDate() + ".",
                "OFFER", "/student/offers", true);
        return offer;
    }

    // ---------------------------------------------------------------- student

    @Transactional
    public Offer accept(Long offerId) {
        Offer offer = mineToRespond(offerId);
        offer.setStatus(Offer.Status.ACCEPTED);
        offer.setRespondedAt(LocalDateTime.now());
        notifyStaff("Offer accepted - verification needed",
                "A student accepted an offer from " + offer.getCompanyName() + ". Please verify it.");
        if (offer.getCompanyUserId() != null) {
            publisher.notifyUser(offer.getCompanyUserId(), "Offer accepted",
                    "Your offer for " + offer.getRole() + " was accepted.", "OFFER", "/company/offers", true);
        }
        return offer;
    }

    @Transactional
    public Offer decline(Long offerId) {
        Offer offer = mineToRespond(offerId);
        offer.setStatus(Offer.Status.DECLINED);
        offer.setRespondedAt(LocalDateTime.now());
        if (offer.getCompanyUserId() != null) {
            publisher.notifyUser(offer.getCompanyUserId(), "Offer declined",
                    "Your offer for " + offer.getRole() + " was declined by the candidate.", "OFFER", "/company/offers", true);
        }
        return offer;
    }

    private Offer mineToRespond(Long offerId) {
        Offer offer = get(offerId);
        if (!offer.getStudentUserId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("Not your offer");
        }
        if (offer.getStatus() != Offer.Status.ISSUED) {
            throw ApiException.badRequest("This offer can no longer be changed");
        }
        if (offer.getResponseDeadline() != null && offer.getResponseDeadline().isBefore(LocalDateTime.now())) {
            offer.setStatus(Offer.Status.EXPIRED);
            throw ApiException.badRequest("The response deadline has passed");
        }
        return offer;
    }

    @Transactional
    public Offer reportOffCampus(String companyName, String role, BigDecimal ctcLpa, MultipartFile letter) {
        if (letter == null || letter.isEmpty()) {
            throw ApiException.badRequest("Upload the offer letter as proof");
        }
        Offer offer = offers.save(Offer.builder()
                .studentUserId(CurrentUser.id())
                .companyName(companyName.trim())
                .role(role.trim())
                .ctcLpa(ctcLpa)
                .tier(policyService.get().tierFor(ctcLpa))
                .offerLetterPath(storeLetter(letter))
                .status(Offer.Status.ACCEPTED)
                .offCampus(true)
                .respondedAt(LocalDateTime.now())
                .build());
        notifyStaff("Off-campus offer reported", "A student reported an off-campus offer from " + companyName + ".");
        return offer;
    }

    // ---------------------------------------------------------------- TPO

    @Audited(action = "OFFER_VERIFIED", target = "#offerId")
    @Transactional
    public Offer verify(Long offerId) {
        Offer offer = get(offerId);
        if (offer.getStatus() != Offer.Status.ACCEPTED) {
            throw ApiException.badRequest("Only accepted offers can be verified");
        }
        offer.setStatus(Offer.Status.VERIFIED);
        offer.setVerifiedBy(CurrentUser.id());
        offer.setVerifiedAt(LocalDateTime.now());
        StudentInfo student = clients.student(offer.getStudentUserId());
        Certificate cert = certificateService.issue(offer, student);
        publisher.publish(RoutingKeys.STUDENT_PLACED, new Events.StudentPlacedEvent(
                offer.getStudentUserId(), offer.getId(), offer.getCompanyName(), offer.getCtcLpa(), offer.getTier().name()));
        publisher.notifyUser(offer.getStudentUserId(), "Placement verified",
                "Your offer from " + offer.getCompanyName() + " is verified. Your placement certificate ("
                        + cert.getVerificationCode() + ") is ready to download.",
                "PLACED", "/student/offers", true);
        return offer;
    }

    @Audited(action = "OFFER_REJECTED", target = "#offerId")
    @Transactional
    public Offer reject(Long offerId, String remarks) {
        Offer offer = get(offerId);
        if (offer.getStatus() != Offer.Status.ACCEPTED) {
            throw ApiException.badRequest("Only accepted offers awaiting verification can be rejected");
        }
        offer.setStatus(Offer.Status.REJECTED);
        offer.setRemarks(remarks);
        publisher.notifyUser(offer.getStudentUserId(), "Offer verification failed",
                "The placement office could not verify your offer from " + offer.getCompanyName()
                        + (remarks == null || remarks.isBlank() ? "." : ": " + remarks), "OFFER", "/student/offers", true);
        return offer;
    }

    // ---------------------------------------------------------------- reads

    public Offer get(Long id) {
        return offers.findById(id).orElseThrow(() -> ApiException.notFound("Offer not found"));
    }

    public Offer getVisible(Long id) {
        Offer o = get(id);
        UserPrincipal me = CurrentUser.get();
        boolean ok = me.isStaff()
                || (me.role() == Role.STUDENT && o.getStudentUserId().equals(me.userId()))
                || (me.role() == Role.COMPANY && me.userId().equals(o.getCompanyUserId()));
        if (!ok) {
            throw ApiException.forbidden("You cannot view this offer");
        }
        return o;
    }

    public List<OfferView> listForCurrentUser(Offer.Status status) {
        UserPrincipal me = CurrentUser.get();
        List<Offer> list = switch (me.role()) {
            case STUDENT -> offers.findByStudentUserIdOrderByCreatedAtDesc(me.userId());
            case COMPANY -> offers.findByCompanyUserIdOrderByCreatedAtDesc(me.userId());
            default -> status == null ? offers.findAllByOrderByCreatedAtDesc() : offers.findByStatusOrderByCreatedAtDesc(status);
        };
        return views(list);
    }

    public byte[] letter(Long offerId) {
        Offer o = getVisible(offerId);
        if (o.getOfferLetterPath() == null) {
            throw ApiException.notFound("No offer letter uploaded");
        }
        return storage.read(o.getOfferLetterPath());
    }

    public List<OfferView> views(List<Offer> list) {
        Map<String, StudentInfo> students = list.isEmpty() ? Map.of()
                : clients.students(list.stream().map(Offer::getStudentUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(StudentInfo::userId, Function.identity(), (a, b) -> a));
        return list.stream().map(o -> {
            StudentInfo s = students.get(o.getStudentUserId());
            Long certId = o.getStatus() == Offer.Status.VERIFIED
                    ? certificates.findByOfferId(o.getId()).map(Certificate::getId).orElse(null) : null;
            return new OfferView(o.getId(), o.getApplicationId(), o.getJobId(), o.getStudentUserId(),
                    s == null ? null : s.name(), s == null ? null : s.regNo(), o.getCompanyName(), o.getRole(),
                    o.getCtcLpa(), o.getTier(), o.getStatus(), o.isOffCampus(), o.getResponseDeadline(),
                    o.getRespondedAt(), o.getVerifiedAt(), o.getRemarks(), o.getOfferLetterPath() != null, certId,
                    o.getCreatedAt());
        }).toList();
    }

    private String storeLetter(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > 5 * 1024 * 1024) {
                throw ApiException.badRequest("Offer letter must be under 5 MB");
            }
            String type = tika.detect(bytes);
            if (!"application/pdf".equals(type)) {
                throw ApiException.badRequest("Offer letter must be a PDF");
            }
            return storage.save("offers", bytes, "pdf");
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not read the offer letter");
        }
    }

    private void notifyStaff(String title, String message) {
        // TPOs see pending verifications on their dashboard; this is a reminder
        publisher.publish(RoutingKeys.NOTIFICATION, new Events.NotificationEvent(
                List.of("ROLE:TPO"), title, message, "OFFER_VERIFICATION", "/tpo/offers", false, List.of(), title));
    }
}

package com.placify.placement.web;

import com.placify.common.security.CurrentUser;
import com.placify.common.web.ApiException;
import com.placify.placement.domain.Certificate;
import com.placify.placement.domain.Offer;
import com.placify.placement.repository.CertificateRepository;
import com.placify.placement.service.CertificateService;
import com.placify.placement.service.OfferService;
import com.placify.placement.web.PlacementDtos.CertificateView;
import com.placify.placement.web.PlacementDtos.NoteRequest;
import com.placify.placement.web.PlacementDtos.OfferView;
import com.placify.placement.web.PlacementDtos.VerificationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Offers and certificates")
@RestController
@RequestMapping("/api/placement")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offers;
    private final CertificateService certificateService;
    private final CertificateRepository certificates;

    @Operation(summary = "Issue an offer to a selected candidate (PDF offer letter optional)")
    @PostMapping(value = "/applications/{applicationId}/offer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public OfferView issue(@PathVariable Long applicationId,
                           @RequestParam(required = false) String role,
                           @RequestParam(required = false) BigDecimal ctcLpa,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime responseDeadline,
                           @RequestPart(value = "file", required = false) MultipartFile file) {
        return offers.views(List.of(offers.issue(applicationId, role, ctcLpa, responseDeadline, file))).get(0);
    }

    @GetMapping("/offers")
    public List<OfferView> list(@RequestParam(required = false) Offer.Status status) {
        return offers.listForCurrentUser(status);
    }

    @PostMapping("/offers/{id}/accept")
    @PreAuthorize("hasRole('STUDENT')")
    public OfferView accept(@PathVariable Long id) {
        return offers.views(List.of(offers.accept(id))).get(0);
    }

    @PostMapping("/offers/{id}/decline")
    @PreAuthorize("hasRole('STUDENT')")
    public OfferView decline(@PathVariable Long id) {
        return offers.views(List.of(offers.decline(id))).get(0);
    }

    @Operation(summary = "Report an off-campus offer with the offer letter (verified by the TPO)")
    @PostMapping(value = "/offers/off-campus", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public OfferView offCampus(@RequestParam String companyName,
                               @RequestParam String role,
                               @RequestParam(required = false) BigDecimal ctcLpa,
                               @RequestPart("file") MultipartFile file) {
        return offers.views(List.of(offers.reportOffCampus(companyName, role, ctcLpa, file))).get(0);
    }

    @PostMapping("/offers/{id}/verify")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public OfferView verify(@PathVariable Long id) {
        return offers.views(List.of(offers.verify(id))).get(0);
    }

    @PostMapping("/offers/{id}/reject")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public OfferView reject(@PathVariable Long id, @RequestBody(required = false) NoteRequest req) {
        return offers.views(List.of(offers.reject(id, req == null ? null : req.note()))).get(0);
    }

    @GetMapping("/offers/{id}/letter")
    public ResponseEntity<byte[]> letter(@PathVariable Long id) {
        return pdf(offers.letter(id), "offer-letter-" + id + ".pdf");
    }

    // ---------------------------------------------------------------- certificates

    @GetMapping("/certificates/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<CertificateView> myCertificates() {
        return certificates.findByStudentUserIdOrderByIssuedAtDesc(CurrentUser.id()).stream()
                .map(certificateService::view).toList();
    }

    @GetMapping("/certificates/{id}/pdf")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable Long id) {
        Certificate c = certificateService.get(id);
        var me = CurrentUser.get();
        if (!me.isStaff() && !c.getStudentUserId().equals(me.userId())) {
            throw ApiException.forbidden("Not your certificate");
        }
        return pdf(certificateService.pdf(c), "placement-certificate-" + c.getRegNo() + ".pdf");
    }

    @Operation(summary = "Public: verify a certificate by the code in its QR")
    @GetMapping("/certificates/verify/{code}")
    public VerificationResult verify(@PathVariable String code) {
        return certificateService.verify(code);
    }

    private static ResponseEntity<byte[]> pdf(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(bytes);
    }
}

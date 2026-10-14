package com.placify.placement.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.placify.common.web.ApiException;
import com.placify.placement.client.StudentInfo;
import com.placify.placement.domain.Certificate;
import com.placify.placement.domain.Offer;
import com.placify.placement.repository.CertificateRepository;
import com.placify.placement.web.PlacementDtos.CertificateView;
import com.placify.placement.web.PlacementDtos.VerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/**
 * Generates the placement certificate PDF (OpenPDF) with a QR code (ZXing)
 * that links to the public verification page.
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final CertificateRepository repository;
    private final SecureRandom random = new SecureRandom();

    @Value("${placify.placement.public-base-url:http://localhost:3000}")
    private String publicBaseUrl;

    @Transactional
    public Certificate issue(Offer offer, StudentInfo student) {
        return repository.findByOfferId(offer.getId()).orElseGet(() -> {
            byte[] bytes = new byte[12];
            random.nextBytes(bytes);
            return repository.save(Certificate.builder()
                    .offerId(offer.getId())
                    .studentUserId(offer.getStudentUserId())
                    .verificationCode("PLC-" + HexFormat.of().formatHex(bytes).toUpperCase())
                    .studentName(student.name())
                    .regNo(student.regNo())
                    .branch(student.branch())
                    .companyName(offer.getCompanyName())
                    .role(offer.getRole())
                    .ctcLpa(offer.getCtcLpa())
                    .revoked(false)
                    .build());
        });
    }

    public Certificate get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Certificate not found"));
    }

    public VerificationResult verify(String code) {
        return repository.findByVerificationCode(code.trim())
                .map(c -> c.isRevoked()
                        ? new VerificationResult(false, "This certificate has been revoked.", view(c))
                        : new VerificationResult(true, "Genuine placement certificate issued by the Training & Placement Office.", view(c)))
                .orElse(new VerificationResult(false, "No certificate found for this code.", null));
    }

    public String verifyUrl(Certificate c) {
        return publicBaseUrl.replaceAll("/$", "") + "/verify/" + c.getVerificationCode();
    }

    public CertificateView view(Certificate c) {
        return new CertificateView(c.getId(), c.getVerificationCode(), c.getStudentName(), c.getRegNo(), c.getBranch(),
                c.getCompanyName(), c.getRole(), c.getCtcLpa(), c.getIssuedAt(), c.isRevoked());
    }

    /** Renders the certificate as an A4 landscape PDF. */
    public byte[] pdf(Certificate c) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            // border
            PdfContentByte canvas = writer.getDirectContent();
            Rectangle page = doc.getPageSize();
            canvas.setColorStroke(new Color(20, 20, 20));
            canvas.setLineWidth(4f);
            canvas.rectangle(20, 20, page.getWidth() - 40, page.getHeight() - 40);
            canvas.stroke();
            canvas.setColorStroke(new Color(42, 157, 143));
            canvas.setLineWidth(1.5f);
            canvas.rectangle(30, 30, page.getWidth() - 60, page.getHeight() - 60);
            canvas.stroke();

            Font brand = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(42, 157, 143));
            Font title = new Font(Font.TIMES_ROMAN, 34, Font.BOLD, new Color(20, 20, 20));
            Font body = new Font(Font.TIMES_ROMAN, 16, Font.NORMAL, Color.DARK_GRAY);
            Font strong = new Font(Font.TIMES_ROMAN, 22, Font.BOLD, Color.BLACK);
            Font small = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);

            doc.add(centered("CAREERPILOT · TRAINING & PLACEMENT OFFICE", brand, 10));
            doc.add(centered("Certificate of Placement", title, 24));
            doc.add(centered("This is to certify that", body, 8));
            doc.add(centered(c.getStudentName(), strong, 4));
            doc.add(centered("Registration No. " + c.getRegNo() + (c.getBranch() == null ? "" : "  ·  " + c.getBranch()), body, 14));
            doc.add(centered("has been placed at", body, 8));
            doc.add(centered(c.getCompanyName(), strong, 4));
            String ctc = c.getCtcLpa() == null ? "" : "  ·  CTC " + c.getCtcLpa().stripTrailingZeros().toPlainString() + " LPA";
            doc.add(centered("as " + c.getRole() + ctc, body, 18));
            doc.add(centered("Issued on " + DATE.format(c.getIssuedAt()), body, 6));

            Image qr = Image.getInstance(qrPng(verifyUrl(c)));
            qr.scaleAbsolute(110, 110);
            qr.setAbsolutePosition(page.getWidth() - 160, 50);
            doc.add(qr);

            Paragraph code = new Paragraph("Verify at " + verifyUrl(c) + "\nCertificate ID: " + c.getVerificationCode(), small);
            code.setAlignment(Element.ALIGN_LEFT);
            code.setSpacingBefore(40);
            doc.add(code);
            doc.add(new Chunk(" "));

            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException | WriterException ex) {
            throw new IllegalStateException("Could not generate certificate PDF", ex);
        }
    }

    private static Paragraph centered(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    static byte[] qrPng(String content) throws WriterException, IOException {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300,
                Map.of(EncodeHintType.MARGIN, 1));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        }
    }
}

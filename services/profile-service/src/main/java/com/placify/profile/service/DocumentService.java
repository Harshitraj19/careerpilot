package com.placify.profile.service;

import com.placify.common.web.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Validates uploaded files by their real content (Apache Tika, not the file
 * name) and extracts resume text with Apache PDFBox.
 */
@Component
public class DocumentService {

    private static final long MAX_RESUME_BYTES = 5L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final Tika tika = new Tika();

    /** An uploaded document with its type and extracted text. */
    public record Document(byte[] bytes, String mime, String extension, String text) {
    }

    /**
     * Reads a PDF or Word (.docx) file, or plain text when {@code allowText} is set, and extracts
     * its text. Used for resumes and for job-description files in the ATS check.
     */
    public Document readDocument(MultipartFile file, boolean allowText) {
        byte[] bytes = read(file, MAX_RESUME_BYTES);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String type = tika.detect(bytes, name);
        if ("application/pdf".equals(type)) {
            return new Document(bytes, type, "pdf", extractText(bytes));
        }
        boolean zipContainer = type.contains("zip") || type.contains("ooxml");
        if (DOCX.equals(type) || (name.endsWith(".docx") && zipContainer)) {
            return new Document(bytes, DOCX, "docx", extractDocx(bytes));
        }
        if (allowText && type.startsWith("text/")) {
            return new Document(bytes, "text/plain", "txt", new String(bytes, StandardCharsets.UTF_8));
        }
        throw ApiException.badRequest("INVALID_FILE_TYPE", allowText
                ? "Upload a PDF, Word (.docx) or text file"
                : "Upload your resume as a PDF or Word (.docx) file");
    }

    public String extractDocx(byte[] docx) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception ex) {
            throw ApiException.badRequest("The Word file could not be read. Please upload a valid .docx file.");
        }
    }

    /** @return the detected MIME type */
    public String checkImage(byte[] bytes) {
        String type = tika.detect(bytes);
        if (!IMAGE_TYPES.contains(type)) {
            throw ApiException.badRequest("INVALID_FILE_TYPE", "Only JPG, PNG or WEBP images are allowed");
        }
        return type;
    }

    public byte[] readImage(MultipartFile file) {
        byte[] bytes = read(file, MAX_IMAGE_BYTES);
        checkImage(bytes);
        return bytes;
    }

    public String extensionFor(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case DOCX -> "docx";
            default -> "jpg";
        };
    }

    public String detect(byte[] bytes) {
        return tika.detect(bytes);
    }

    public String extractText(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException ex) {
            throw ApiException.badRequest("The PDF could not be read. Please upload a valid PDF.");
        }
    }

    private byte[] read(MultipartFile file, long max) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file uploaded");
        }
        if (file.getSize() > max) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "File is larger than " + (max / (1024 * 1024)) + " MB");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not read upload");
        }
    }
}

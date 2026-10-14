package com.placify.profile.service;

import com.placify.common.demo.DemoData;
import com.placify.common.storage.FileStorageService;
import com.placify.common.util.Texts;
import com.placify.profile.domain.CompanyProfile;
import com.placify.profile.domain.StudentProfile;
import com.placify.profile.repository.CompanyProfileRepository;
import com.placify.profile.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Demo student and company profiles (DEMO_DATA=true), including a generated PDF resume per student. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = DemoData.PROPERTY, havingValue = "true")
public class DemoProfileSeeder implements ApplicationRunner {

    private final StudentProfileRepository students;
    private final CompanyProfileRepository companies;
    private final FileStorageService storage;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (DemoData.Student s : DemoData.STUDENTS) {
            StudentProfile profile = students.findById(s.id()).orElseGet(() -> students.save(newProfile(s)));
            if (profile.getResumePath() == null) {
                attachResume(profile, s);
            }
        }
        for (DemoData.Company c : DemoData.COMPANIES) {
            if (!companies.existsById(c.id())) {
                companies.save(CompanyProfile.builder().userId(c.id()).companyName(c.name()).email(c.email())
                        .industry(c.industry()).website(c.website()).about(c.about()).location("Bengaluru")
                        .employeeCount(1200).build());
            }
        }
    }

    private static StudentProfile newProfile(DemoData.Student s) {
        return StudentProfile.builder()
                .userId(s.id()).regNo(s.regNo()).name(s.name()).email(s.email()).phone(s.phone())
                .branch(s.branch()).batch(s.batch()).cgpa(s.cgpa()).backlogs(s.backlogs())
                .tenthPercentage(new BigDecimal("91.20")).twelfthPercentage(new BigDecimal("88.40"))
                .skills(Texts.join(s.skills()))
                .summary(summary(s))
                .projectsJson("[{\"title\":\"Campus Connect\",\"description\":\"" + projectDescription(s)
                        + "\",\"techStack\":\"" + String.join(", ", topSkills(s)) + "\",\"link\":null}]")
                .githubUrl("https://github.com/" + s.regNo().toLowerCase())
                .build();
    }

    private void attachResume(StudentProfile profile, DemoData.Student s) {
        List<String> lines = resumeLines(s);
        try {
            profile.setResumePath(storage.save("resumes", pdf(lines), "pdf"));
            profile.setResumeOriginalName(s.regNo() + "_resume.pdf");
            profile.setResumeUploadedAt(LocalDateTime.now());
            profile.setResumeText(String.join("\n", lines));
            students.save(profile);
        } catch (IOException ex) {
            log.warn("Could not create the demo resume for {}: {}", s.regNo(), ex.getMessage());
        }
    }

    private static List<String> resumeLines(DemoData.Student s) {
        List<String> lines = new ArrayList<>();
        lines.add(s.name());
        lines.add(s.email() + "  |  " + s.phone() + "  |  github.com/" + s.regNo().toLowerCase());
        lines.add("");
        lines.add("SUMMARY");
        lines.add(summary(s));
        lines.add("");
        lines.add("EDUCATION");
        lines.add("B.Tech " + s.branch() + " (" + s.batch() + ")  -  CGPA " + s.cgpa());
        lines.add("Class XII: 88.4%   Class X: 91.2%");
        lines.add("");
        lines.add("SKILLS");
        lines.add(String.join(", ", s.skills()));
        lines.add("");
        lines.add("PROJECTS");
        lines.add("Campus Connect (" + String.join(", ", topSkills(s)) + ")");
        lines.add(projectDescription(s));
        return lines;
    }

    private static byte[] pdf(List<String> lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - 60;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    boolean heading = i == 0 || (!line.isEmpty() && line.equals(line.toUpperCase()) && line.length() < 20);
                    cs.beginText();
                    cs.setFont(heading ? bold : regular, i == 0 ? 18 : heading ? 12 : 10.5f);
                    cs.newLineAtOffset(56, y);
                    cs.showText(line);
                    cs.endText();
                    y -= i == 0 ? 22 : 15;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> topSkills(DemoData.Student s) {
        return s.skills().subList(0, Math.min(3, s.skills().size()));
    }

    private static String summary(DemoData.Student s) {
        return "Final-year " + s.branch() + " student who enjoys building real products with " + s.skills().get(0) + ".";
    }

    private static String projectDescription(DemoData.Student s) {
        return "A " + s.skills().get(0) + " app that helps students find study groups; 300+ active users.";
    }
}

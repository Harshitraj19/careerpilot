package com.placify.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.common.audit.Audited;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import com.placify.common.messaging.Events;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.storage.FileStorageService;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import com.placify.profile.domain.StudentProfile;
import com.placify.profile.repository.AtsResultRepository;
import com.placify.profile.repository.StudentProfileRepository;
import com.placify.profile.web.ProfileDtos.AcademicUpdate;
import com.placify.profile.web.ProfileDtos.Completeness;
import com.placify.profile.web.ProfileDtos.ProjectItem;
import com.placify.profile.web.ProfileDtos.StudentProfileUpdate;
import com.placify.profile.web.ProfileDtos.StudentProfileView;
import com.placify.profile.web.ProfileDtos.StudentSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Student profile, photo, resume upload and AI resume parsing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileRepository repository;
    private final AtsResultRepository atsRepository;
    private final FileStorageService storage;
    private final DocumentService documents;
    private final AiClient aiClient;
    private final InternalClient internalClient;
    private final ObjectMapper mapper;

    // --------------------------------------------------------------- read

    public StudentProfile get(String userId) {
        return repository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Student profile not found"));
    }

    /**
     * Students can see their own profile; TPO/Admin any; a company only the
     * students who applied to one of its jobs (checked with placement-service).
     */
    public StudentProfile getForViewer(String studentUserId) {
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.STUDENT && !me.userId().equals(studentUserId)) {
            throw ApiException.forbidden("You can only view your own profile");
        }
        if (me.role() == Role.COMPANY) {
            Boolean allowed = internalClient.get(ServiceNames.PLACEMENT,
                    "/internal/placement/access?companyUserId=" + me.userId() + "&studentUserId=" + studentUserId,
                    Boolean.class);
            if (!Boolean.TRUE.equals(allowed)) {
                throw ApiException.forbidden("This student has not applied to your jobs");
            }
        }
        return get(studentUserId);
    }

    public StudentProfileView view(StudentProfile p) {
        return new StudentProfileView(p.getUserId(), p.getRegNo(), p.getName(), p.getEmail(), p.getPhone(),
                p.getBranch(), p.getBatch(), p.getCgpa(), p.getTenthPercentage(), p.getTwelfthPercentage(),
                p.getBacklogs(), new ArrayList<>(Texts.toList(p.getSkills())), Texts.toList(p.getCertifications()),
                projects(p), p.getInternships(), p.getAchievements(), p.getSummary(), p.getGithubUrl(),
                p.getLinkedinUrl(), p.getPortfolioUrl(), p.getPhotoPath() != null, p.getResumePath() != null,
                p.getResumeOriginalName(), p.getResumeUploadedAt(), completeness(p), p.getUpdatedAt());
    }

    public StudentSummary summary(StudentProfile p) {
        Integer latest = atsRepository.findFirstByStudentUserIdOrderByCreatedAtDesc(p.getUserId())
                .map(a -> a.getScore()).orElse(null);
        return new StudentSummary(p.getUserId(), p.getRegNo(), p.getName(), p.getEmail(), p.getBranch(), p.getBatch(),
                p.getCgpa(), p.getTenthPercentage(), p.getTwelfthPercentage(), p.getBacklogs(),
                Texts.toList(p.getSkills()), completeness(p).percent(), p.getResumePath() != null,
                p.getPhotoPath() != null, latest, atsRepository.bestScore(p.getUserId()));
    }

    /** Profile completeness: each field group has a weight, total 100. */
    public Completeness completeness(StudentProfile p) {
        int score = 0;
        List<String> missing = new ArrayList<>();
        score += check(p.getPhone() != null && !p.getPhone().isBlank(), 5, "Phone number", missing);
        score += check(p.getCgpa() != null, 10, "CGPA (ask the placement office)", missing);
        score += check(p.getTenthPercentage() != null, 5, "10th percentage", missing);
        score += check(p.getTwelfthPercentage() != null, 5, "12th percentage", missing);
        score += check(Texts.toList(p.getSkills()).size() >= 3, 15, "At least 3 skills", missing);
        score += check(!projects(p).isEmpty(), 15, "Projects", missing);
        score += check(!Texts.isBlank(p.getCertifications()), 5, "Certifications", missing);
        score += check(!Texts.isBlank(p.getInternships()), 5, "Internship / experience", missing);
        score += check(!Texts.isBlank(p.getSummary()), 5, "Profile summary", missing);
        score += check(!Texts.isBlank(p.getGithubUrl()), 5, "GitHub link", missing);
        score += check(!Texts.isBlank(p.getLinkedinUrl()), 5, "LinkedIn link", missing);
        score += check(p.getPhotoPath() != null, 5, "Profile photo", missing);
        score += check(p.getResumePath() != null, 15, "Resume PDF", missing);
        return new Completeness(score, missing);
    }

    private static int check(boolean ok, int weight, String label, List<String> missing) {
        if (!ok) {
            missing.add(label);
        }
        return ok ? weight : 0;
    }

    private List<ProjectItem> projects(StudentProfile p) {
        if (Texts.isBlank(p.getProjectsJson())) {
            return List.of();
        }
        try {
            return mapper.readValue(p.getProjectsJson(), new TypeReference<List<ProjectItem>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    // --------------------------------------------------------------- write

    @Transactional
    public StudentProfile update(String userId, StudentProfileUpdate req) {
        StudentProfile p = get(userId);
        if (req.phone() != null) {
            p.setPhone(req.phone().trim());
        }
        if (req.tenthPercentage() != null) {
            p.setTenthPercentage(req.tenthPercentage());
        }
        if (req.twelfthPercentage() != null) {
            p.setTwelfthPercentage(req.twelfthPercentage());
        }
        if (req.skills() != null) {
            p.setSkills(Texts.join(new LinkedHashSet<>(req.skills())));
        }
        if (req.certifications() != null) {
            p.setCertifications(Texts.join(req.certifications()));
        }
        if (req.projects() != null) {
            p.setProjectsJson(toJson(req.projects()));
        }
        if (req.internships() != null) {
            p.setInternships(req.internships());
        }
        if (req.achievements() != null) {
            p.setAchievements(req.achievements());
        }
        if (req.summary() != null) {
            p.setSummary(req.summary());
        }
        if (req.githubUrl() != null) {
            p.setGithubUrl(cleanUrl(req.githubUrl()));
        }
        if (req.linkedinUrl() != null) {
            p.setLinkedinUrl(cleanUrl(req.linkedinUrl()));
        }
        if (req.portfolioUrl() != null) {
            p.setPortfolioUrl(cleanUrl(req.portfolioUrl()));
        }
        // backlogs are self-declared at first and verified/overridden by the TPO
        if (req.backlogs() != null) {
            p.setBacklogs(req.backlogs());
        }
        return p;
    }

    @Audited(action = "ACADEMIC_DATA_UPDATED", target = "#userId")
    @Transactional
    public StudentProfile updateAcademic(String userId, AcademicUpdate req) {
        StudentProfile p = get(userId);
        if (req.branch() != null && !req.branch().isBlank()) {
            p.setBranch(req.branch().trim());
        }
        if (req.batch() != null) {
            p.setBatch(req.batch());
        }
        if (req.cgpa() != null) {
            p.setCgpa(req.cgpa());
        }
        if (req.backlogs() != null) {
            p.setBacklogs(req.backlogs());
        }
        return p;
    }

    private static String cleanUrl(String url) {
        String u = url.trim();
        if (u.isEmpty()) {
            return null;
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        return u;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("Invalid projects data");
        }
    }

    @Transactional
    public void uploadPhoto(String userId, MultipartFile file) {
        StudentProfile p = get(userId);
        byte[] bytes = documents.readImage(file);
        String path = storage.save("photos", bytes, documents.extensionFor(documents.detect(bytes)));
        storage.delete(p.getPhotoPath());
        p.setPhotoPath(path);
    }

    @Transactional
    public StudentProfile uploadResume(String userId, MultipartFile file) {
        StudentProfile p = get(userId);
        DocumentService.Document doc = documents.readDocument(file, false);
        String text = doc.text();
        String path = storage.save("resumes", doc.bytes(), doc.extension());
        storage.delete(p.getResumePath());
        p.setResumePath(path);
        p.setResumeOriginalName(Texts.truncate(file.getOriginalFilename(), 255));
        p.setResumeUploadedAt(LocalDateTime.now());
        p.setResumeText(text);
        return p;
    }

    @Transactional
    public void deleteResume(String userId) {
        StudentProfile p = get(userId);
        storage.delete(p.getResumePath());
        p.setResumePath(null);
        p.setResumeOriginalName(null);
        p.setResumeUploadedAt(null);
        p.setResumeText(null);
    }

    /**
     * Sends the resume text to the AI parser. With {@code apply=true} the result is
     * merged into the profile: skills are added, empty fields are filled, nothing
     * the student already wrote is overwritten.
     */
    @Transactional
    public AiClient.ParsedResume parseResume(String userId, boolean apply) {
        StudentProfile p = get(userId);
        if (Texts.isBlank(p.getResumeText())) {
            throw ApiException.badRequest("Upload a resume PDF first");
        }
        AiClient.ParsedResume parsed;
        try {
            parsed = aiClient.parseResume(p.getResumeText());
        } catch (Exception ex) {
            throw ApiException.unavailable("AI resume parsing is not available right now");
        }
        if (parsed == null) {
            throw ApiException.unavailable("AI resume parsing returned no result");
        }
        if (apply) {
            if (parsed.skills() != null && !parsed.skills().isEmpty()) {
                Set<String> merged = new LinkedHashSet<>(Texts.toList(p.getSkills()));
                parsed.skills().stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(merged::add);
                p.setSkills(Texts.join(merged));
            }
            if (Texts.isBlank(p.getCertifications()) && parsed.certifications() != null) {
                p.setCertifications(Texts.join(parsed.certifications()));
            }
            if (Texts.isBlank(p.getProjectsJson()) && parsed.projects() != null && !parsed.projects().isEmpty()) {
                List<ProjectItem> items = parsed.projects().stream()
                        .map(pp -> new ProjectItem(pp.title(), pp.description(), pp.techStack(), pp.link()))
                        .toList();
                p.setProjectsJson(toJson(items));
            }
            if (Texts.isBlank(p.getInternships()) && !Texts.isBlank(parsed.internships())) {
                p.setInternships(parsed.internships());
            }
            if (Texts.isBlank(p.getAchievements()) && parsed.achievements() != null && !parsed.achievements().isEmpty()) {
                p.setAchievements(String.join("\n", parsed.achievements()));
            }
            if (Texts.isBlank(p.getSummary()) && !Texts.isBlank(parsed.summary())) {
                p.setSummary(parsed.summary());
            }
            if (Texts.isBlank(p.getGithubUrl()) && !Texts.isBlank(parsed.githubUrl())) {
                p.setGithubUrl(cleanUrl(parsed.githubUrl()));
            }
            if (Texts.isBlank(p.getLinkedinUrl()) && !Texts.isBlank(parsed.linkedinUrl())) {
                p.setLinkedinUrl(cleanUrl(parsed.linkedinUrl()));
            }
        }
        return parsed;
    }

    // --------------------------------------------------------------- events

    @Transactional
    public void onStudentImported(Events.StudentImportedEvent e) {
        StudentProfile p = repository.findById(e.userId()).orElseGet(() -> StudentProfile.builder()
                .userId(e.userId())
                .backlogs(0)
                .build());
        p.setRegNo(e.regNo());
        p.setName(e.name());
        p.setEmail(e.email());
        p.setBranch(e.branch());
        p.setBatch(e.batch());
        if (e.cgpa() != null) {
            p.setCgpa(e.cgpa());
        }
        if (e.phone() != null) {
            p.setPhone(e.phone());
        }
        repository.save(p);
        log.info("Created/updated profile for student {}", e.regNo());
    }
}

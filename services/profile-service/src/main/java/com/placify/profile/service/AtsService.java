package com.placify.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import com.placify.profile.ats.AtsScorer;
import com.placify.profile.domain.AtsResult;
import com.placify.profile.domain.StudentProfile;
import com.placify.profile.repository.AtsResultRepository;
import com.placify.profile.web.ProfileDtos.AtsRequest;
import com.placify.profile.web.ProfileDtos.AtsStats;
import com.placify.profile.web.ProfileDtos.AtsView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Runs the ATS check of the student's uploaded resume against a job description,
 * optionally asks the AI for written feedback, and keeps the history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtsService {

    private final StudentProfileService students;
    private final AtsResultRepository repository;
    private final AiClient aiClient;
    private final ObjectMapper mapper;
    private final DocumentService documents;

    @Transactional
    public AtsView check(String studentUserId, AtsRequest req) {
        StudentProfile p = students.get(studentUserId);
        if (Texts.isBlank(p.getResumeText())) {
            throw ApiException.badRequest("Upload your resume PDF before running the ATS check");
        }
        return score(studentUserId, req.jobTitle(), req.companyName(), p.getResumeText(), req.jobDescription(), req.useAi());
    }

    /**
     * ATS check with uploaded documents: the job description as a file (PDF, DOCX, TXT) or pasted
     * text, and the resume as a file or, when none is given, the resume on the student's profile.
     */
    @Transactional
    public AtsView checkFiles(String studentUserId, String jobTitle, String companyName, String jobDescription,
                              MultipartFile jobDescriptionFile, MultipartFile resumeFile, boolean useAi) {
        String jd = jobDescriptionFile != null && !jobDescriptionFile.isEmpty()
                ? documents.readDocument(jobDescriptionFile, true).text()
                : jobDescription;
        if (Texts.isBlank(jd) || jd.strip().length() < 50) {
            throw ApiException.badRequest("Upload the job description file or paste the full job description (at least 50 characters)");
        }
        String resume;
        if (resumeFile != null && !resumeFile.isEmpty()) {
            resume = documents.readDocument(resumeFile, false).text();
        } else {
            resume = students.get(studentUserId).getResumeText();
            if (Texts.isBlank(resume)) {
                throw ApiException.badRequest("Upload a resume file here or on your profile before running the ATS check");
            }
        }
        if (Texts.isBlank(resume) || resume.strip().length() < 100) {
            throw ApiException.badRequest("No readable text was found in the resume. Use a text-based PDF or Word file, not a scanned image.");
        }
        String title = Texts.isBlank(jobTitle) && jobDescriptionFile != null && jobDescriptionFile.getOriginalFilename() != null
                ? jobDescriptionFile.getOriginalFilename().replaceAll("\\.[A-Za-z0-9]+$", "")
                : jobTitle;
        return score(studentUserId, Texts.truncate(title, 255), Texts.truncate(companyName, 255), resume,
                Texts.truncate(jd.strip(), 20000), useAi);
    }

    private AtsView score(String studentUserId, String jobTitle, String companyName, String resume, String jobDescription,
                          boolean useAi) {
        double semantic = aiClient.similarity(Texts.truncate(resume, 6000), Texts.truncate(jobDescription, 6000));
        AtsScorer.Result r = AtsScorer.score(resume, jobDescription, semantic);

        Object aiFeedback = null;
        String aiJson = null;
        if (useAi) {
            AiClient.ResumeFeedback fb = aiClient.feedback(Texts.truncate(resume, 8000), Texts.truncate(jobDescription, 6000));
            if (fb != null) {
                aiFeedback = fb;
                try {
                    aiJson = mapper.writeValueAsString(fb);
                } catch (JsonProcessingException ex) {
                    log.warn("Could not store AI feedback: {}", ex.getMessage());
                }
            }
        }

        AtsResult saved = repository.save(AtsResult.builder()
                .studentUserId(studentUserId)
                .jobTitle(jobTitle)
                .companyName(companyName)
                .score(r.score())
                .keywordScore(r.keywordScore())
                .semanticScore(r.semanticScore())
                .contentScore(r.contentScore())
                .structureScore(r.structureScore())
                .impactScore(r.impactScore())
                .parseabilityScore(r.parseabilityScore())
                .matchedKeywords(String.join(", ", r.matchedKeywords()))
                .missingKeywords(String.join(", ", r.missingKeywords()))
                .tips(String.join("\n", r.tips()))
                .aiFeedbackJson(aiJson)
                .build());
        return AtsView.of(saved, aiFeedback);
    }

    public Page<AtsView> history(String studentUserId, Pageable pageable) {
        return repository.findByStudentUserIdOrderByCreatedAtDesc(studentUserId, pageable)
                .map(r -> AtsView.of(r, readFeedback(r.getAiFeedbackJson())));
    }

    public AtsView one(String studentUserId, Long id) {
        AtsResult r = repository.findById(id).orElseThrow(() -> ApiException.notFound("ATS result not found"));
        if (!r.getStudentUserId().equals(studentUserId)) {
            throw ApiException.forbidden("Not your ATS result");
        }
        return AtsView.of(r, readFeedback(r.getAiFeedbackJson()));
    }

    public AtsStats stats(String studentUserId) {
        Integer latest = repository.findFirstByStudentUserIdOrderByCreatedAtDesc(studentUserId)
                .map(AtsResult::getScore).orElse(null);
        return new AtsStats(repository.countByStudentUserId(studentUserId), latest,
                repository.bestScore(studentUserId), repository.averageScore(studentUserId));
    }

    private Object readFeedback(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}

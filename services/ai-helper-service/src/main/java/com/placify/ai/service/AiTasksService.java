package com.placify.ai.service;

import com.placify.ai.llm.LlmGateway;
import com.placify.ai.offline.CodeAnalyzer;
import com.placify.ai.offline.ExperienceDigest;
import com.placify.ai.offline.McqFactory;
import com.placify.ai.offline.OfflineInterviewer;
import com.placify.ai.offline.ResumeAnalyzer;
import com.placify.ai.web.AiDtos.CodeReview;
import com.placify.ai.web.AiDtos.CodeReviewRequest;
import com.placify.ai.web.AiDtos.ExperienceSummary;
import com.placify.ai.web.AiDtos.GeneratedMcq;
import com.placify.ai.web.AiDtos.InterviewQuestion;
import com.placify.ai.web.AiDtos.InterviewQuestionsRequest;
import com.placify.ai.web.AiDtos.McqGenerateRequest;
import com.placify.ai.web.AiDtos.ParsedResume;
import com.placify.ai.web.AiDtos.ResumeFeedback;
import com.placify.ai.web.AiDtos.SummarizeRequest;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-shot AI tasks used by other services and by the student/TPO UI. Each task uses the language
 * model when one is configured and falls back to the built-in engine otherwise.
 */
@Service
@RequiredArgsConstructor
public class AiTasksService {

    private final LlmGateway llm;
    private final ResumeAnalyzer resumes;
    private final CodeAnalyzer codeAnalyzer;
    private final McqFactory mcqFactory;
    private final OfflineInterviewer interviewer;
    private final ExperienceDigest digest;

    // ------------------------------------------------------------- code review

    public CodeReview reviewCode(CodeReviewRequest req) {
        if (Texts.isBlank(req.code())) {
            throw ApiException.badRequest("Code is required");
        }
        return llm.withFallback(() -> llm.structured("""
                You are a senior engineer reviewing a student's ACCEPTED solution to a coding problem.
                Return JSON: summary (2-3 sentences on the approach), timeComplexity and spaceComplexity (Big-O),
                strengths (2-4), improvements (2-4 concrete suggestions: naming, structure, a better algorithm if one exists),
                edgeCases (2-4 the student should test), qualityScore (0-10).
                """, """
                PROBLEM: %s
                %s

                LANGUAGE: %s
                CODE:
                ```
                %s
                ```
                """.formatted(req.title(), Texts.truncate(req.description(), 3000), req.language(),
                Texts.truncate(req.code(), 20000)), CodeReview.class),
                () -> codeAnalyzer.review(req.title(), req.description(), req.code(), req.language()));
    }

    // ------------------------------------------------------------- resume

    public ParsedResume parseResume(String text) {
        if (Texts.isBlank(text)) {
            throw ApiException.badRequest("Resume text is empty");
        }
        return llm.withFallback(() -> llm.structured("""
                Extract structured data from the resume text. Do not invent anything that is not in the text;
                use null or empty lists for missing fields.
                Return JSON: skills (list of short skill names), certifications (list), projects (list of
                {title, description (1-2 sentences), techStack (comma separated), link}), internships (short text),
                achievements (list), summary (2-3 sentence professional summary written from the resume),
                githubUrl, linkedinUrl.
                """, "RESUME:\n" + Texts.truncate(text, 15000), ParsedResume.class), () -> resumes.parse(text));
    }

    public ResumeFeedback resumeFeedback(String resume, String jd) {
        if (Texts.isBlank(resume)) {
            throw ApiException.badRequest("Resume text is empty");
        }
        return llm.withFallback(() -> llm.structured("""
                You are a campus placement resume coach. Compare the resume with the job description
                (or, if none is given, with typical software-engineering fresher roles).
                Return JSON: fitScore (0-100), strengths (2-4), weaknesses (2-4),
                suggestions (3-6 concrete, actionable edits, e.g. "Quantify the impact of the CareerPilot project").
                """, "JOB DESCRIPTION:\n" + (Texts.isBlank(jd) ? "(general software engineering fresher)" : Texts.truncate(jd, 5000))
                + "\n\nRESUME:\n" + Texts.truncate(resume, 12000), ResumeFeedback.class), () -> resumes.feedback(resume, jd));
    }

    // ------------------------------------------------------------- MCQ generation

    public List<GeneratedMcq> generateMcqs(McqGenerateRequest req) {
        int count = Math.max(1, Math.min(20, req.count()));
        List<GeneratedMcq> raw = llm.withFallback(() -> llm.structured("""
                You write original aptitude/technical multiple-choice questions for campus placement tests.
                Each question must have exactly 4 distinct options, exactly ONE correct answer, and a short
                explanation that shows the working. Questions must be self-contained, unambiguous and not copied
                from well-known sources. Return a JSON array of {question, options (4 strings), correctIndex (0-3), explanation}.
                """, "TOPIC: %s\nSUBTOPIC: %s\nDIFFICULTY: %s\nCOUNT: %d".formatted(req.topic(),
                Texts.isBlank(req.subtopic()) ? "any" : req.subtopic(), req.difficulty(), count),
                new ParameterizedTypeReference<List<GeneratedMcq>>() {
                }), () -> mcqFactory.generate(req.topic(), req.difficulty(), count));
        return validMcqs(raw, count);
    }

    /** Drops malformed questions so the TPO never reviews broken items. */
    static List<GeneratedMcq> validMcqs(List<GeneratedMcq> raw, int limit) {
        List<GeneratedMcq> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (GeneratedMcq q : raw) {
            if (q == null || Texts.isBlank(q.question()) || q.options() == null || q.options().size() != 4
                    || q.correctIndex() == null || q.correctIndex() < 0 || q.correctIndex() > 3) {
                continue;
            }
            Set<String> distinct = q.options().stream().map(o -> o == null ? "" : o.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            if (distinct.size() != 4 || distinct.contains("")) {
                continue;
            }
            if (seen.add(q.question().trim().toLowerCase(Locale.ROOT))) {
                out.add(q);
            }
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------- interview prep

    public List<InterviewQuestion> interviewQuestions(InterviewQuestionsRequest req) {
        return llm.withFallback(() -> llm.structured("""
                You prepare students for campus placement interviews. Generate likely interview questions for the
                role and skills given: mix TECHNICAL (fundamentals + skills), PROJECT and HR questions, easy to hard.
                Return a JSON array of {question, type (TECHNICAL|PROJECT|HR), difficulty (EASY|MEDIUM|HARD),
                hint (one line on what a good answer covers)}.
                """, "ROLE: %s\nSKILLS: %s\nCOUNT: %d".formatted(req.role(),
                req.skills() == null || req.skills().isEmpty() ? "not specified" : String.join(", ", req.skills()),
                req.count()), new ParameterizedTypeReference<List<InterviewQuestion>>() {
                }), () -> interviewer.questions(req.role(), req.skills(), req.count()));
    }

    public ExperienceSummary summarizeExperiences(SummarizeRequest req) {
        if (req.experiences() == null || req.experiences().isEmpty()) {
            throw ApiException.badRequest("No experiences to summarise");
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String e : req.experiences()) {
            sb.append("EXPERIENCE ").append(i++).append(":\n").append(Texts.truncate(e, 3000)).append("\n\n");
            if (sb.length() > 30000) {
                break;
            }
        }
        return llm.withFallback(() -> llm.structured("""
                Summarise these interview experiences shared by seniors for one company so juniors can prepare.
                Return JSON: summary (4-6 sentences), commonTopics (5-10 frequently asked topics),
                roundPattern (e.g. "Online test -> Technical 1 -> Technical 2 -> HR"), difficulty (EASY|MEDIUM|HARD),
                tips (3-6 practical tips).
                """, "COMPANY: " + req.company() + "\n\n" + sb, ExperienceSummary.class),
                () -> digest.summarise(req.company(), req.experiences()));
    }
}

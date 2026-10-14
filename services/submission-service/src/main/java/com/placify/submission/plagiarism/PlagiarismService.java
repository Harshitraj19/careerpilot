package com.placify.submission.plagiarism;

import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.submission.client.SubmissionClients;
import com.placify.submission.domain.Language;
import com.placify.submission.domain.PlagiarismCheck;
import com.placify.submission.domain.PlagiarismPair;
import com.placify.submission.domain.Submission;
import com.placify.submission.repository.PlagiarismCheckRepository;
import com.placify.submission.repository.PlagiarismPairRepository;
import com.placify.submission.repository.SubmissionRepository;
import com.placify.submission.service.SubmissionService;
import com.placify.submission.web.SubmissionDtos.PairDetail;
import com.placify.submission.web.SubmissionDtos.PairView;
import com.placify.submission.web.SubmissionDtos.PlagiarismCheckView;
import com.placify.submission.web.SubmissionDtos.PlagiarismRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares the submissions of a test or contest pairwise and flags suspiciously
 * similar pairs for a human to review side by side.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlagiarismService {

    private final SubmissionRepository submissions;
    private final PlagiarismCheckRepository checks;
    private final PlagiarismPairRepository pairs;
    private final SubmissionService submissionService;
    private final SubmissionClients clients;
    private final JPlagEngine jplag;
    private final EventPublisher publisher;

    @Value("${placify.plagiarism.default-threshold:0.8}")
    private double defaultThreshold;

    @Audited(action = "PLAGIARISM_CHECK_RUN", target = "#req.scopeId()")
    @Transactional
    public PlagiarismCheckView run(PlagiarismRequest req) {
        authorize(req.scope(), req.scopeId());
        double threshold = req.threshold() == null ? defaultThreshold : req.threshold().doubleValue();

        List<Submission> all = req.scope() == PlagiarismCheck.Scope.ASSESSMENT
                ? submissions.findByTestIdAndModeOrderBySubmissionTimeDesc(req.scopeId(), Submission.Mode.ASSESSMENT)
                : submissions.findByContestIdAndModeOrderBySubmissionTimeDesc(req.scopeId(), Submission.Mode.CONTEST);

        // latest submission of each student for each question (list is newest first)
        Map<String, Submission> latest = new LinkedHashMap<>();
        for (Submission s : all) {
            latest.putIfAbsent(s.getUserId() + "|" + s.getQuestionId(), s);
        }
        // compare only the same question in the same language
        Map<String, List<Submission>> groups = latest.values().stream()
                .collect(Collectors.groupingBy(s -> s.getQuestionId() + "|" + s.getLanguageId()));

        PlagiarismCheck check = checks.save(PlagiarismCheck.builder()
                .scope(req.scope()).scopeId(req.scopeId())
                .threshold(BigDecimal.valueOf(threshold).setScale(3, RoundingMode.HALF_UP))
                .requestedBy(CurrentUser.id())
                .build());

        Set<String> engines = new LinkedHashSet<>();
        int flagged = 0;
        for (List<Submission> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (JPlagEngine.Pair p : compareGroup(group, engines)) {
                if (p.similarity() >= threshold) {
                    Submission a = latestById(group, p.keyA());
                    Submission b = latestById(group, p.keyB());
                    if (a == null || b == null || a.getUserId().equals(b.getUserId())) {
                        continue;
                    }
                    pairs.save(PlagiarismPair.builder().checkId(check.getId()).questionId(a.getQuestionId())
                            .submissionA(a.getId()).submissionB(b.getId()).userA(a.getUserId()).userB(b.getUserId())
                            .similarity(BigDecimal.valueOf(p.similarity()).setScale(4, RoundingMode.HALF_UP))
                            .build());
                    flagged++;
                }
            }
        }
        check.setSubmissionsCompared(latest.size());
        check.setPairsFlagged(flagged);
        check.setEngine(engines.isEmpty() ? "none" : String.join(" + ", engines));
        if (flagged > 0) {
            publisher.notifyUser(CurrentUser.id(), "Plagiarism check finished",
                    flagged + " suspicious pair(s) found. Review them side by side.", "PLAGIARISM", null, false);
        }
        return view(check);
    }

    private List<JPlagEngine.Pair> compareGroup(List<Submission> group, Set<String> engines) {
        Language lang = Language.byJudge0Id(group.get(0).getLanguageId()).orElse(Language.CPP);
        Map<String, String> code = new LinkedHashMap<>();
        group.forEach(s -> code.put(s.getId(), s.getSourceCode()));

        Optional<de.jplag.Language> jplagLanguage = jplag.languageFor(lang);
        if (jplagLanguage.isPresent()) {
            try {
                List<JPlagEngine.Pair> result = jplag.compare(jplagLanguage.get(), lang.extension(), code);
                engines.add("JPlag");
                return result;
            } catch (Exception ex) {
                log.warn("JPlag failed for {} ({}); using built-in winnowing", lang, ex.getMessage());
            }
        }
        engines.add("Winnowing");
        List<JPlagEngine.Pair> out = new ArrayList<>();
        List<Submission> list = new ArrayList<>(group);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                out.add(new JPlagEngine.Pair(list.get(i).getId(), list.get(j).getId(),
                        WinnowingSimilarity.similarity(list.get(i).getSourceCode(), list.get(j).getSourceCode())));
            }
        }
        return out;
    }

    private static Submission latestById(List<Submission> group, String id) {
        return group.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }

    private void authorize(PlagiarismCheck.Scope scope, Long scopeId) {
        UserPrincipal me = CurrentUser.get();
        if (me.isStaff()) {
            return;
        }
        if (me.role() == Role.COMPANY && scope == PlagiarismCheck.Scope.ASSESSMENT
                && me.userId().equals(clients.testOwner(scopeId))) {
            return;
        }
        throw ApiException.forbidden("You cannot run plagiarism checks for this scope");
    }

    public List<PlagiarismCheckView> list(PlagiarismCheck.Scope scope, Long scopeId) {
        authorize(scope, scopeId);
        return checks.findByScopeAndScopeIdOrderByCreatedAtDesc(scope, scopeId).stream().map(this::view).toList();
    }

    public PlagiarismCheckView get(Long id) {
        PlagiarismCheck c = checks.findById(id).orElseThrow(() -> ApiException.notFound("Check not found"));
        authorize(c.getScope(), c.getScopeId());
        return view(c);
    }

    public PairDetail pair(Long pairId) {
        PlagiarismPair p = pairs.findById(pairId).orElseThrow(() -> ApiException.notFound("Pair not found"));
        PlagiarismCheck c = checks.findById(p.getCheckId()).orElseThrow();
        authorize(c.getScope(), c.getScopeId());
        Submission a = submissions.findById(p.getSubmissionA()).orElseThrow();
        Submission b = submissions.findById(p.getSubmissionB()).orElseThrow();
        return new PairDetail(pairView(p), submissionService.view(a, true), submissionService.view(b, true));
    }

    private PlagiarismCheckView view(PlagiarismCheck c) {
        return new PlagiarismCheckView(c.getId(), c.getScope(), c.getScopeId(), c.getThreshold(), c.getSubmissionsCompared(),
                c.getPairsFlagged(), c.getEngine(), c.getCreatedAt(),
                pairs.findByCheckIdOrderBySimilarityDesc(c.getId()).stream().map(PlagiarismService::pairView).toList());
    }

    private static PairView pairView(PlagiarismPair p) {
        return new PairView(p.getId(), p.getQuestionId(), p.getSubmissionA(), p.getSubmissionB(), p.getUserA(), p.getUserB(),
                p.getSimilarity());
    }
}

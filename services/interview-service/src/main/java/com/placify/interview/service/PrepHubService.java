package com.placify.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.common.web.ApiException;
import com.placify.interview.client.InterviewClients;
import com.placify.interview.client.InterviewClients.CompanyStats;
import com.placify.interview.client.InterviewClients.ExperienceSummary;
import com.placify.interview.domain.InterviewExperience;
import com.placify.interview.domain.PrepSummary;
import com.placify.interview.repository.InterviewExperienceRepository;
import com.placify.interview.repository.PrepSummaryRepository;
import com.placify.interview.web.InterviewDtos.ExperienceView;
import com.placify.interview.web.InterviewDtos.PrepHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Company Prep Hub: everything a student needs before a company's drive -
 * year-wise selection stats and round pattern (from placement), the most
 * helpful experiences, and an AI summary of all of them (cached).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepHubService {

    private final InterviewExperienceRepository experiences;
    private final PrepSummaryRepository summaries;
    private final InterviewClients clients;
    private final ExperienceService experienceService;
    private final ObjectMapper mapper;

    @Value("${placify.interview.summary-refresh-after:2}")
    private int refreshAfter;

    @Transactional
    public PrepHub hub(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw ApiException.badRequest("Company name is required");
        }
        String key = InterviewExperience.keyOf(companyName);
        List<InterviewExperience> approved = experiences.findByCompanyKeyAndStatusOrderByUpvotesDescCreatedAtDesc(
                key, InterviewExperience.Status.APPROVED);
        CompanyStats stats = clients.companyStats(companyName.trim());

        PrepSummary cached = summaries.findById(key).orElse(null);
        ExperienceSummary summary = read(cached);
        boolean stale = cached == null || approved.size() - cached.getExperienceCount() >= refreshAfter;
        if (!approved.isEmpty() && stale) {
            try {
                summary = clients.summarize(approved.get(0).getCompanyName(), approved.stream().limit(15)
                        .map(PrepHubService::asText).toList());
                cached = save(key, approved.get(0).getCompanyName(), summary, approved.size(), cached);
            } catch (Exception ex) {
                log.warn("AI summary for {} not refreshed: {}", companyName, ex.getMessage());
            }
        }
        List<ExperienceView> top = approved.stream().limit(5).map(e -> experienceService.view(e, Set.of())).toList();
        String display = !approved.isEmpty() ? approved.get(0).getCompanyName()
                : stats != null && stats.companyName() != null ? stats.companyName() : companyName.trim();
        return new PrepHub(display, stats, summary, cached == null ? null : cached.getGeneratedAt(), approved.size(), top);
    }

    static String asText(InterviewExperience e) {
        return "Role: " + e.getJobRole() + " (" + e.getYear() + ", " + e.getResult() + ", " + e.getDifficulty() + ")\n"
                + (e.getRoundsSummary() == null ? "" : "Rounds: " + e.getRoundsSummary() + "\n")
                + e.getContent()
                + (e.getQuestionsAsked() == null ? "" : "\nQuestions: " + e.getQuestionsAsked())
                + (e.getTips() == null ? "" : "\nTips: " + e.getTips());
    }

    private ExperienceSummary read(PrepSummary cached) {
        if (cached == null) {
            return null;
        }
        try {
            return mapper.readValue(cached.getSummaryJson(), ExperienceSummary.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private PrepSummary save(String key, String name, ExperienceSummary summary, int count, PrepSummary existing)
            throws JsonProcessingException {
        PrepSummary s = existing == null ? PrepSummary.builder().companyKey(key).build() : existing;
        s.setCompanyName(name);
        s.setSummaryJson(mapper.writeValueAsString(summary));
        s.setExperienceCount(count);
        s.setGeneratedAt(LocalDateTime.now());
        return summaries.save(s);
    }
}

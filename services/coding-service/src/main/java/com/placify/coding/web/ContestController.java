package com.placify.coding.web;

import com.placify.coding.service.ContestService;
import com.placify.coding.service.QuestionService;
import com.placify.coding.web.CodingDtos.ContestRequest;
import com.placify.coding.web.CodingDtos.ContestView;
import com.placify.coding.web.CodingDtos.QuestionView;
import com.placify.common.messaging.Events;
import com.placify.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Contests")
@RestController
@RequestMapping("/api/coding/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService service;
    private final QuestionService questions;

    @GetMapping
    public List<ContestView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ContestView one(@PathVariable Long id) {
        return service.view(service.get(id), CurrentUser.get(), true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public ContestView create(@Valid @RequestBody ContestRequest req) {
        return service.view(service.create(req), CurrentUser.get(), true);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public ContestView update(@PathVariable Long id, @Valid @RequestBody ContestRequest req) {
        return service.view(service.update(id, req), CurrentUser.get(), true);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ContestView join(@PathVariable Long id) {
        service.join(id);
        return service.view(service.get(id), CurrentUser.get(), true);
    }

    @GetMapping("/{id}/leaderboard")
    public List<Events.LeaderboardEntry> leaderboard(@PathVariable Long id, @RequestParam(defaultValue = "50") int limit) {
        return service.leaderboard(id, Math.min(limit, 200));
    }

    @GetMapping("/{id}/questions/{questionId}")
    public QuestionView question(@PathVariable Long id, @PathVariable String questionId) {
        return questions.view(service.contestQuestion(id, questionId), null);
    }
}

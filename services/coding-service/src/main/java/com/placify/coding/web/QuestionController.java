package com.placify.coding.web;

import com.placify.coding.domain.Question;
import com.placify.coding.repository.TestcaseRepository;
import com.placify.coding.repository.UserQuestionStatusRepository;
import com.placify.coding.service.QuestionService;
import com.placify.coding.web.CodingDtos.QuestionRequest;
import com.placify.coding.web.CodingDtos.QuestionSummary;
import com.placify.coding.web.CodingDtos.QuestionView;
import com.placify.coding.web.CodingDtos.TestcaseRequest;
import com.placify.coding.web.CodingDtos.TestcaseView;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

@Tag(name = "Coding questions")
@RestController
@RequestMapping("/api/coding")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService service;
    private final TestcaseRepository testcases;
    private final UserQuestionStatusRepository statuses;

    @GetMapping("/questions")
    public Page<QuestionSummary> list(@RequestParam(required = false) Question.Difficulty difficulty,
                                      @RequestParam(required = false) String tag,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.search(difficulty, tag, q, PageRequest.of(page, Math.min(size, 100), Sort.by("rating")));
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return service.tags();
    }

    @GetMapping("/questions/{id}")
    public QuestionView one(@PathVariable String id) {
        Question q = service.getForViewer(id);
        Boolean solved = CurrentUser.role() == Role.STUDENT
                ? statuses.findByUserIdAndQuestionId(CurrentUser.id(), id).map(s -> s.isSolved()).orElse(false)
                : null;
        return service.view(q, solved);
    }

    @Operation(summary = "Create a question with test cases (companies create test-only questions)")
    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public QuestionView create(@Valid @RequestBody QuestionRequest req) {
        return service.view(service.create(req), null);
    }

    @PutMapping("/questions/{id}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public QuestionView update(@PathVariable String id, @Valid @RequestBody QuestionRequest req) {
        return service.view(service.update(id, req), null);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @Operation(summary = "Test cases. Students only see public ones; hidden outputs are never exposed.")
    @GetMapping("/questions/{id}/testcases")
    public List<TestcaseView> testcases(@PathVariable String id) {
        Question q = service.getForViewer(id);
        if (service.canEdit(q)) {
            return testcases.findByQuestionIdOrderByCreatedAtAsc(id).stream().map(t -> TestcaseView.of(t, false)).toList();
        }
        return testcases.findByQuestionIdAndHiddenFalseOrderByCreatedAtAsc(id).stream().map(t -> TestcaseView.of(t, true)).toList();
    }

    @PostMapping("/questions/{id}/testcases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public TestcaseView addTestcase(@PathVariable String id, @Valid @RequestBody TestcaseRequest req) {
        service.editable(id);
        return TestcaseView.of(service.addTestcase(id, req), false);
    }

    @PutMapping("/testcases/{id}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public TestcaseView updateTestcase(@PathVariable String id, @Valid @RequestBody TestcaseRequest req) {
        return TestcaseView.of(service.updateTestcase(id, req), false);
    }

    @DeleteMapping("/testcases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public void deleteTestcase(@PathVariable String id) {
        service.deleteTestcase(id);
    }
}

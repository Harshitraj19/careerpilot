package com.placify.notification.web;

import com.placify.common.audit.Audited;
import com.placify.notification.domain.StudentQuery;
import com.placify.notification.service.QueryService;
import com.placify.notification.web.QueryDtos.CategoryView;
import com.placify.notification.web.QueryDtos.NewQuery;
import com.placify.notification.web.QueryDtos.QueryDetail;
import com.placify.notification.web.QueryDtos.QueryStats;
import com.placify.notification.web.QueryDtos.QueryView;
import com.placify.notification.web.QueryDtos.Reply;
import com.placify.notification.web.QueryDtos.Resolve;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Student help desk: raise, discuss and resolve queries with the placement office. */
@Tag(name = "Student queries")
@RestController
@RequestMapping("/api/notifications/queries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT','TPO','ADMIN')")
public class QueryController {

    private final QueryService service;

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return Arrays.stream(StudentQuery.Category.values()).map(c -> new CategoryView(c, c.label())).toList();
    }

    @Operation(summary = "Students see their own queries; the placement office sees all of them")
    @GetMapping
    public Page<QueryView> list(@RequestParam(required = false) StudentQuery.Status status,
                                @RequestParam(required = false) StudentQuery.Category category,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.list(status, category, q, page, size);
    }

    @GetMapping("/stats")
    public QueryStats stats() {
        return service.stats();
    }

    @Operation(summary = "Raise a query (students)")
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public QueryDetail create(@Valid @RequestBody NewQuery req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public QueryDetail one(@PathVariable Long id) {
        return service.one(id);
    }

    @PostMapping("/{id}/messages")
    public QueryDetail reply(@PathVariable Long id, @Valid @RequestBody Reply req) {
        return service.reply(id, req.message());
    }

    @Operation(summary = "Mark a query resolved, optionally with a final answer")
    @PostMapping("/{id}/resolve")
    @Audited(action = "QUERY_RESOLVED", target = "#id")
    public QueryDetail resolve(@PathVariable Long id, @Valid @RequestBody(required = false) Resolve req) {
        return service.resolve(id, req == null ? null : req.message());
    }

    @PostMapping("/{id}/reopen")
    public QueryDetail reopen(@PathVariable Long id) {
        return service.reopen(id);
    }
}

package com.placify.notification.web;

import com.placify.notification.domain.StudentQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Requests and views of the student help desk. */
public final class QueryDtos {

    private QueryDtos() {
    }

    public record NewQuery(
            @NotNull StudentQuery.Category category,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String message) {
    }

    public record Reply(@NotBlank @Size(max = 4000) String message) {
    }

    public record Resolve(@Size(max = 4000) String message) {
    }

    public record QueryView(
            Long id,
            String reference,
            String studentUserId,
            String studentName,
            String studentLogin,
            StudentQuery.Category category,
            String categoryLabel,
            String subject,
            StudentQuery.Status status,
            boolean awaitingStudent,
            String resolvedByName,
            int messageCount,
            String lastMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime resolvedAt) {
    }

    public record MessageView(Long id, String authorName, String authorRole, boolean mine, String message, LocalDateTime createdAt) {
    }

    public record QueryDetail(QueryView query, List<MessageView> messages) {
    }

    public record CategoryView(StudentQuery.Category value, String label) {
    }

    public record QueryStats(long pending, long resolved) {
    }
}

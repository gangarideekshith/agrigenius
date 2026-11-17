package com.agrigenius.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Request and response bodies of the chat API (docs/api/API_CONTRACT.md, chat section). */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record CreateSessionRequest(
            @NotBlank @Pattern(regexp = "en|hi|te|kn|ta", message = "must be one of en, hi, te, kn, ta")
            String language,
            @Size(max = 120) String title) {

        public CreateSessionRequest {
            language = language == null ? null : language.strip().toLowerCase(Locale.ROOT);
            title = title == null || title.isBlank() ? null : title.strip();
        }
    }

    /** Length is checked by the service after control characters are removed. */
    public record SendMessageRequest(@NotNull String content) {

        @Override
        public String toString() {
            return "SendMessageRequest[content=<redacted>]";
        }
    }

    public record SessionResponse(UUID id, String title, String language, String status, int messageCount,
            Instant createdAt, Instant lastMessageAt) {
    }

    public record SourceResponse(String title, String publisher, String url, String licence) {
    }

    public record MessageResponse(UUID id, String role, String status, String content, String language,
            List<SourceResponse> sources, List<String> toolsUsed, String uncertainty, Instant createdAt) {
    }

    public record UserMessageResponse(UUID id, String content, Instant createdAt) {
    }

    public record AssistantMessageResponse(UUID id, String status, String language, String content,
            List<SourceResponse> sources, List<String> toolsUsed, String uncertainty, Instant createdAt) {
    }

    public record UsageResponse(int remainingMessagesToday) {
    }

    public record SendMessageResponse(UserMessageResponse userMessage, AssistantMessageResponse assistantMessage,
            UsageResponse usage, String notice) {
    }

    /** A page of results. {@code page} is zero-based. */
    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }
}

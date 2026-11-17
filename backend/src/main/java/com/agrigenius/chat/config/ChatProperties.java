package com.agrigenius.chat.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Chat limits and model controls (decisions C1, C9). */
@Validated
@ConfigurationProperties(prefix = "agrigenius.chat")
public record ChatProperties(
        @NotBlank @Pattern(regexp = "low|medium|high") String effort,
        @Min(1) int messagesPerWindow,
        @NotNull Duration window,
        @Min(1) int dailyMessageLimit,
        @Min(1) long dailyTokenBudget,
        @Min(0) int historyMessages,
        @Min(1) int maxContentChars,
        @Min(1) int maxMessagesPerSession,
        @Min(1) int maxActiveSessions,
        @NotNull Duration providerTimeout) {
}

package com.agrigenius.reminder.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Reminder limits and the due-reminder scanner. The scanner interval itself is read by
 * {@code ReminderDueScanner}'s {@code @Scheduled} placeholder (agrigenius.reminders.scanner.interval).
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.reminders")
public record ReminderProperties(
        @NotBlank String defaultTimeZone,
        @Min(1) @Max(10_000) int maxPendingPerFarmer,
        @Min(1) @Max(3650) int maxDaysAhead,
        @NotNull Duration pastGrace,
        @Min(1) @Max(60) int upcomingDefaultDays,
        @Min(1) @Max(365) int upcomingMaxDays,
        @Min(1) @Max(500) int listLimit,
        @NotNull @Valid Scanner scanner) {

    public record Scanner(boolean enabled, @NotNull Duration interval, @Min(1) @Max(1000) int batchSize,
            @Min(1) @Max(1000) int maxBatchesPerRun) {
    }
}

package com.agrigenius.reminder.api.dto;

import com.agrigenius.reminder.domain.ReminderCategory;
import com.agrigenius.reminder.domain.ReminderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Create or replace a reminder. The local date and time are interpreted in {@code timeZone}
 * (IANA id, default Asia/Kolkata). There is deliberately no owner field: the owner is always the
 * signed-in farmer from the access token, and unknown JSON properties are ignored.
 */
public record ReminderRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String description,
        @NotNull ReminderCategory category,
        @Size(max = 60) String cropName,
        @NotNull LocalDate scheduledDate,
        @NotNull LocalTime scheduledTime,
        @Size(max = 40) String timeZone,
        ReminderPriority priority) {
}

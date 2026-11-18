package com.agrigenius.reminder.domain;

/** Kinds of farm work a reminder can be for. Stored by name; keep in sync with ck_reminders_category (V5). */
public enum ReminderCategory {
    IRRIGATION,
    FERTILIZER,
    PESTICIDE,
    WEEDING,
    CROP_INSPECTION,
    SOIL_TESTING,
    HARVESTING,
    SOWING,
    GENERAL,
    CUSTOM
}

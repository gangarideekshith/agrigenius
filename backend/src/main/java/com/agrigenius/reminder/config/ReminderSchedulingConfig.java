package com.agrigenius.reminder.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on Spring's scheduler only when the due-reminder scanner is enabled. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "agrigenius.reminders.scanner", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ReminderSchedulingConfig {
}

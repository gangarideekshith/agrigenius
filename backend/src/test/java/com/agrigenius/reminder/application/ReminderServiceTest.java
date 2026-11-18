package com.agrigenius.reminder.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrigenius.reminder.api.dto.ReminderRequest;
import com.agrigenius.reminder.api.dto.ReminderResponse;
import com.agrigenius.reminder.config.ReminderProperties;
import com.agrigenius.reminder.domain.Reminder;
import com.agrigenius.reminder.domain.ReminderCategory;
import com.agrigenius.reminder.domain.ReminderDetails;
import com.agrigenius.reminder.domain.ReminderPriority;
import com.agrigenius.reminder.domain.ReminderStatus;
import com.agrigenius.reminder.infrastructure.ReminderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Time, time-zone, limit and state rules of {@link ReminderService}, with a fixed clock. */
class ReminderServiceTest {

    // 2030-06-01 10:00 in Asia/Kolkata (UTC+05:30).
    private static final Instant NOW = Instant.parse("2030-06-01T04:30:00Z");
    private static final UUID USER = UUID.randomUUID();

    private final ReminderRepository repository = mock(ReminderRepository.class);
    private ReminderService service;

    @BeforeEach
    void setUp() {
        ReminderProperties properties = new ReminderProperties("Asia/Kolkata", 2, 365, Duration.ofMinutes(2), 7, 60,
                100, new ReminderProperties.Scanner(false, Duration.ofMinutes(1), 200, 20));
        service = new ReminderService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void localDateAndTimeAreInterpretedInTheChosenZone() {
        ReminderResponse response = service.create(USER, request(LocalDate.of(2030, 6, 1), LocalTime.of(18, 0), null));

        assertThat(response.scheduledAt()).isEqualTo(Instant.parse("2030-06-01T12:30:00Z"));
        assertThat(response.timeZone()).isEqualTo("Asia/Kolkata");
        assertThat(response.priority()).isEqualTo(ReminderPriority.MEDIUM);
        assertThat(response.status()).isEqualTo(ReminderStatus.PENDING);

        ReminderResponse utc = service.create(USER, request(LocalDate.of(2030, 6, 1), LocalTime.of(18, 0), "UTC"));
        assertThat(utc.scheduledAt()).isEqualTo(Instant.parse("2030-06-01T18:00:00Z"));
    }

    @Test
    void aTimeWithinTheGracePeriodIsAcceptedButOlderIsNot() {
        // 09:59 local is one minute ago: inside the two-minute grace.
        assertThat(service.create(USER, request(LocalDate.of(2030, 6, 1), LocalTime.of(9, 59), null))).isNotNull();

        assertThatThrownBy(() -> service.create(USER, request(LocalDate.of(2030, 6, 1), LocalTime.of(9, 57), null)))
                .isInstanceOf(ReminderException.class)
                .extracting("code").isEqualTo("REMINDER_IN_PAST");
    }

    @Test
    void timesBeyondTheMaximumHorizonAreRejected() {
        assertThatThrownBy(() -> service.create(USER, request(LocalDate.of(2031, 6, 2), LocalTime.NOON, null)))
                .extracting("code").isEqualTo("REMINDER_TOO_FAR_AHEAD");
    }

    @Test
    void unknownOrNonRegionTimeZonesAreRejected() {
        assertThatThrownBy(() -> service.create(USER, request(LocalDate.of(2030, 6, 2), LocalTime.NOON, "IST")))
                .extracting("code").isEqualTo("INVALID_TIME_ZONE");
        assertThatThrownBy(() -> service.zone("../../etc/passwd")).extracting("code").isEqualTo("INVALID_TIME_ZONE");
        assertThat(service.zone("  ").getId()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void thePendingLimitIsEnforcedOnCreate() {
        when(repository.countByUserIdAndStatus(USER, ReminderStatus.PENDING)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(USER, request(LocalDate.of(2030, 6, 2), LocalTime.NOON, null)))
                .extracting("code").isEqualTo("REMINDER_LIMIT_REACHED");
        verify(repository, never()).save(any());
    }

    @Test
    void reschedulingClearsTheNotifiedMarker() {
        Reminder reminder = existing(Instant.parse("2030-06-01T03:00:00Z"));
        reminder.markNotified(NOW);
        when(repository.findByIdAndUserId(reminder.getId(), USER)).thenReturn(Optional.of(reminder));

        // Same (past) time: allowed, marker kept.
        service.update(USER, reminder.getId(), request(LocalDate.of(2030, 6, 1), LocalTime.of(8, 30), null));
        assertThat(reminder.getNotifiedAt()).isEqualTo(NOW);

        // New future time: marker cleared so it can notify again.
        service.update(USER, reminder.getId(), request(LocalDate.of(2030, 6, 2), LocalTime.of(8, 30), null));
        assertThat(reminder.getNotifiedAt()).isNull();
    }

    @Test
    void otherFarmersRemindersAreReportedAsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, id)).isInstanceOf(ReminderNotFoundException.class);
        assertThatThrownBy(() -> service.complete(USER, id)).isInstanceOf(ReminderNotFoundException.class);
    }

    @Test
    void overdueIsOnlyTrueForPendingRemindersInThePast() {
        Reminder reminder = existing(NOW.minusSeconds(60));
        assertThat(ReminderService.toResponse(reminder, NOW).overdue()).isTrue();
        reminder.complete(NOW);
        assertThat(ReminderService.toResponse(reminder, NOW).overdue()).isFalse();
    }

    private static Reminder existing(Instant at) {
        return new Reminder(UUID.randomUUID(), USER, new ReminderDetails("Irrigate", null, ReminderCategory.IRRIGATION,
                null, at, "Asia/Kolkata", ReminderPriority.MEDIUM), NOW.minusSeconds(86_400));
    }

    private static ReminderRequest request(LocalDate date, LocalTime time, String zone) {
        return new ReminderRequest("Irrigate", null, ReminderCategory.IRRIGATION, null, date, time, zone, null);
    }
}

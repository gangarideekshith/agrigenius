package com.agrigenius.reminder.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrigenius.reminder.application.ReminderDueService.BatchResult;
import com.agrigenius.reminder.config.ReminderProperties;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Batch draining and failure handling of the scheduled scan. */
class ReminderDueScannerTest {

    private final ReminderDueService service = mock(ReminderDueService.class);
    private final ReminderScannerFixture fixture = new ReminderScannerFixture();

    @Test
    void drainsFullBatchesAndStopsAtAPartialOne() {
        when(service.processBatch()).thenReturn(new BatchResult(2, 2), new BatchResult(2, 1), new BatchResult(1, 1));

        fixture.scanner(service, 2, 10).scan();

        verify(service, times(3)).processBatch();
    }

    @Test
    void neverRunsMoreThanTheConfiguredNumberOfBatches() {
        when(service.processBatch()).thenReturn(new BatchResult(2, 2));

        fixture.scanner(service, 2, 3).scan();

        verify(service, times(3)).processBatch();
    }

    @Test
    void aFailingBatchIsLoggedAndDoesNotEscapeTheScheduler() {
        when(service.processBatch()).thenThrow(new IllegalStateException("database unavailable"));

        Assertions.assertThatCode(() -> fixture.scanner(service, 2, 3).scan()).doesNotThrowAnyException();
    }

    private static final class ReminderScannerFixture {
        ReminderDueScanner scanner(ReminderDueService service, int batchSize, int maxBatches) {
            ReminderProperties properties = new ReminderProperties("Asia/Kolkata", 500, 365, Duration.ofMinutes(2), 7,
                    60, 100, new ReminderProperties.Scanner(true, Duration.ofMinutes(1), batchSize, maxBatches));
            return new ReminderDueScanner(service, properties);
        }
    }
}

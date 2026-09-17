package ru.otus.financetracker.infrastructure.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ru.otus.financetracker.application.recurring.RecurringTransactionService;

class RecurringTransactionSchedulerTests {

    @Test
    @DisplayName("Планировщик получает бизнес-дату из внедренных часов")
    void shouldUseBusinessDateFromInjectedClock() {
        var service = mock(RecurringTransactionService.class);
        var clock = Clock.fixed(
                Instant.parse("2026-02-28T00:30:00Z"),
                ZoneId.of("America/Los_Angeles")
        );

        new RecurringTransactionScheduler(service, clock).createDueOccurrences();

        verify(service).createDueOccurrences(LocalDate.of(2026, 2, 27));
    }
}

package ru.otus.financetracker.infrastructure.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.otus.financetracker.application.recurring.RecurringTransactionService;

@Component
public class RecurringTransactionScheduler {

    private final RecurringTransactionService recurringTransactionService;
    private final Clock clock;

    public RecurringTransactionScheduler(
            RecurringTransactionService recurringTransactionService,
            Clock clock
    ) {
        this.recurringTransactionService = recurringTransactionService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "${app.time-zone}")
    public void createDueOccurrences() {
        recurringTransactionService.createDueOccurrences(LocalDate.now(clock));
    }
}

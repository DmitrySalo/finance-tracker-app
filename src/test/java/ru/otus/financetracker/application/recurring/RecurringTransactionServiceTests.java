package ru.otus.financetracker.application.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import ru.otus.financetracker.application.audit.TransactionAuditService;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.application.transactions.TransactionRepository;
import ru.otus.financetracker.domain.categories.Category;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.domain.recurring.RecurringTransaction;
import ru.otus.financetracker.domain.transactions.Transaction;

class RecurringTransactionServiceTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-02-28T12:00:00Z"), ZoneOffset.UTC);
    private final InMemoryRecurringRepository recurringRepository = new InMemoryRecurringRepository();
    private final InMemoryOccurrenceRepository occurrenceRepository = new InMemoryOccurrenceRepository();
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final RecurringTransactionService service = new RecurringTransactionService(recurringRepository, occurrenceRepository,
            categoryRepository, transactionRepository, mock(TransactionAuditService.class), clock);

    @Test
    void shouldCreateOccurrenceOnLastDayOfShortMonthAndAdvanceToMarchThirtyFirst() {
        RecurringTransaction rule = rule(LocalDate.of(2026, 1, 31), 31, true);
        recurringRepository.rules.add(rule);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createDueOccurrences(LocalDate.of(2026, 2, 28));

        assertThat(occurrenceRepository.occurrenceDates).containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28));
        assertThat(recurringRepository.rules.getFirst().nextOccurrenceDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void shouldSkipInactiveRule() {
        recurringRepository.rules.add(rule(LocalDate.of(2026, 2, 28), 28, false));

        service.createDueOccurrences(LocalDate.of(2026, 2, 28));

        assertThat(occurrenceRepository.occurrenceDates).isEmpty();
    }

    @Test
    void shouldBeIdempotentWhenDueProcessingIsRepeated() {
        recurringRepository.rules.add(rule(LocalDate.of(2026, 2, 28), 28, true));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createDueOccurrences(LocalDate.of(2026, 2, 28));
        service.createDueOccurrences(LocalDate.of(2026, 2, 28));

        assertThat(occurrenceRepository.occurrenceDates).containsExactly(LocalDate.of(2026, 2, 28));
    }

    @Test
    void shouldLimitBackfillPerRuleAndPreserveNextOccurrenceDateForNextRun() {
        recurringRepository.rules.add(rule(LocalDate.of(2025, 1, 31), 31, true));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createDueOccurrences(LocalDate.of(2027, 12, 31));

        assertThat(occurrenceRepository.occurrenceDates).hasSize(12);
        assertThat(recurringRepository.rules.getFirst().nextOccurrenceDate()).isEqualTo(LocalDate.of(2026, 1, 31));

        service.createDueOccurrences(LocalDate.of(2027, 12, 31));

        assertThat(occurrenceRepository.occurrenceDates).hasSize(24);
        assertThat(recurringRepository.rules.getFirst().nextOccurrenceDate()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    private RecurringTransaction rule(LocalDate nextOccurrenceDate, int dayOfMonth, boolean active) {
        UUID id = UUID.randomUUID();
        return new RecurringTransaction(id, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"), "USD",
                new BigDecimal("1.00000000"), "Rent", TransactionType.EXPENSE, dayOfMonth, nextOccurrenceDate,
                nextOccurrenceDate, active, clock.instant(), clock.instant(), 0);
    }

    private static final class InMemoryRecurringRepository implements RecurringTransactionRepository {
        private final List<RecurringTransaction> rules = new ArrayList<>();
        @Override public RecurringTransaction save(RecurringTransaction rule) { rules.replaceAll(current -> current.id().equals(rule.id()) ? rule : current); return rule; }
        @Override public Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId) { return rules.stream().filter(rule -> rule.id().equals(id) && rule.userId().equals(userId)).findFirst(); }
        @Override public org.springframework.data.domain.Page<RecurringTransaction> findAllByUserId(UUID userId, org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public List<RecurringTransaction> findActiveDueOnOrBefore(LocalDate date) { return rules.stream().filter(rule -> rule.active() && !rule.nextOccurrenceDate().isAfter(date)).toList(); }
        @Override public void delete(RecurringTransaction rule) { rules.remove(rule); }
    }
    private static final class InMemoryOccurrenceRepository implements RecurringTransactionOccurrenceRepository {
        private final List<LocalDate> occurrenceDates = new ArrayList<>();
        @Override public void save(UUID recurringTransactionId, LocalDate occurrenceDate, UUID transactionId, Instant createdAt) { occurrenceDates.add(occurrenceDate); }
    }
}

package ru.otus.financetracker.application.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ru.otus.financetracker.application.transactions.TransactionService;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

@Import(RecurringTransactionProcessingIntegrationTests.FixedClockConfiguration.class)
class RecurringTransactionProcessingIntegrationTests extends PostgresIntegrationTestSupport {

    private static final Instant PROCESSING_TIME = Instant.parse("2026-02-28T12:00:00Z");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 2, 28);

    @Autowired
    private RecurringTransactionService recurringTransactionService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Удаление созданной повторяющейся операции удаляет ее occurrence")
    void shouldDeleteOccurrenceWhenGeneratedTransactionIsDeleted() {
        References references = insertReferences();
        UUID ruleId = insertDueRule(references);

        recurringTransactionService.createDueOccurrences(DUE_DATE);

        UUID transactionId = jdbcTemplate.queryForObject(
                "SELECT id FROM transactions WHERE recurring_transaction_id = ?",
                UUID.class,
                ruleId
        );
        transactionService.delete(references.userId(), transactionId, 0);

        assertThat(count("SELECT COUNT(*) FROM transactions WHERE id = ?", transactionId)).isZero();
        assertThat(
                count("SELECT COUNT(*) FROM recurring_transaction_occurrences WHERE transaction_id = ?", transactionId)
        ).isZero();
    }

    @Test
    @DisplayName("Одновременная обработка создает единственную повторяющуюся операцию")
    void shouldCreateOneOccurrenceWhenDueProcessingRunsConcurrently() throws Exception {
        References references = insertReferences();
        UUID ruleId = insertDueRule(references);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> processDueOccurrencesInSeparateTransaction(ready, start));
            var second = executor.submit(() -> processDueOccurrencesInSeparateTransaction(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertThat(count("SELECT COUNT(*) FROM transactions WHERE recurring_transaction_id = ?", ruleId)).isEqualTo(1);
        assertThat(
                count("SELECT COUNT(*) FROM recurring_transaction_occurrences WHERE recurring_transaction_id = ?", ruleId)
        ).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_occurrence_date FROM recurring_transactions WHERE id = ?",
                LocalDate.class,
                ruleId
        )).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    private void processDueOccurrencesInSeparateTransaction(
            CountDownLatch ready,
            CountDownLatch start
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent due processing did not start in time.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Concurrent due processing was interrupted.", exception);
            }
            recurringTransactionService.createDueOccurrences(DUE_DATE);
        });
    }

    private References insertReferences() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                userId,
                userId + "@example.test",
                "hash",
                "Test",
                "USD"
        );
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                categoryId,
                userId,
                "Food",
                "EXPENSE",
                "icon",
                "#0A1B2C"
        );
        return new References(userId, categoryId);
    }

    private UUID insertDueRule(References references) {
        UUID ruleId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO recurring_transactions (id, user_id, category_id, category_transaction_type, amount, currency,
                    exchange_rate_to_base, transaction_type, day_of_month, start_date, next_occurrence_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ruleId,
                references.userId(),
                references.categoryId(),
                TransactionType.EXPENSE.name(),
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("1.00000000"),
                TransactionType.EXPENSE.name(),
                31,
                LocalDate.of(2026, 1, 31),
                DUE_DATE
        );
        return ruleId;
    }

    private int count(String sql, UUID id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private record References(UUID userId, UUID categoryId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(PROCESSING_TIME, ZoneOffset.UTC);
        }
    }
}

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import ru.otus.financetracker.application.transactions.TransactionService;
import ru.otus.financetracker.domain.categories.TransactionType;

@SpringBootTest(properties = {
        "JWT_ISSUER=https://issuer.test",
        "JWT_AUDIENCE=finance-tracker-test",
        "JWT_SECRET=test-signing-secret-with-at-least-32-characters",
        "CORS_ALLOWED_ORIGINS=https://frontend.test",
        "MAX_REQUEST_SIZE=1MB",
        "MAX_CSV_FILE_SIZE=512KB",
        "MAX_CSV_ROWS=100",
        "MAX_REQUEST_HEADER_SIZE=8KB"
})
@Import(RecurringTransactionProcessingIntegrationTests.FixedClockConfiguration.class)
@Testcontainers
class RecurringTransactionProcessingIntegrationTests {

    private static final Instant PROCESSING_TIME = Instant.parse("2026-02-28T12:00:00Z");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 2, 28);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private RecurringTransactionService recurringTransactionService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = replica");
            jdbcTemplate.execute(
                    "TRUNCATE TABLE audit_logs, recurring_transaction_occurrences, transactions, recurring_transactions, categories, users CASCADE"
            );
        });
    }

    @Test
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

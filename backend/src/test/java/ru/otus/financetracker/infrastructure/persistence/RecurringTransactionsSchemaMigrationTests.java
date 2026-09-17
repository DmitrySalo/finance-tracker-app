package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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
@Testcontainers
class RecurringTransactionsSchemaMigrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldCreateRecurringRuleAndIdempotentOccurrenceConstraints() {
        var references = references();
        var ruleId = UUID.randomUUID();
        var transactionId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO recurring_transactions (id,user_id,category_id,category_transaction_type,amount,currency,exchange_rate_to_base,transaction_type,day_of_month,start_date,next_occurrence_date) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                ruleId,
                references.userId(),
                references.categoryId(),
                "EXPENSE",
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("1.00000000"),
                "EXPENSE",
                31,
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31)
        );
        jdbcTemplate.update(
                "INSERT INTO transactions (id,user_id,category_id,amount,currency,exchange_rate_to_base,transaction_date,transaction_type,recurring_transaction_id) VALUES (?,?,?,?,?,?,?,?,?)",
                transactionId,
                references.userId(),
                references.categoryId(),
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("1.00000000"),
                LocalDate.of(2026, 1, 31),
                "EXPENSE",
                ruleId
        );
        jdbcTemplate.update(
                "INSERT INTO recurring_transaction_occurrences (id,recurring_transaction_id,occurrence_date,transaction_id) VALUES (?,?,?,?)",
                UUID.randomUUID(),
                ruleId,
                LocalDate.of(2026, 1, 31),
                transactionId
        );

        assertThat(indexDefinition("idx_recurring_transactions_active_next_occurrence"))
                .contains("active, next_occurrence_date");

        var duplicateTransactionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO transactions (id,user_id,category_id,amount,currency,exchange_rate_to_base,transaction_date,transaction_type,recurring_transaction_id) VALUES (?,?,?,?,?,?,?,?,?)",
                duplicateTransactionId,
                references.userId(),
                references.categoryId(),
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("1.00000000"),
                LocalDate.of(2026, 1, 31),
                "EXPENSE",
                ruleId
        );

        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO recurring_transaction_occurrences (id,recurring_transaction_id,occurrence_date,transaction_id) VALUES (?,?,?,?)",
                        UUID.randomUUID(),
                        ruleId,
                        LocalDate.of(2026, 1, 31),
                        duplicateTransactionId
                )
        ).hasStackTraceContaining("uq_recurring_occurrences_rule_date");
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO recurring_transactions (id,user_id,category_id,category_transaction_type,amount,currency,exchange_rate_to_base,transaction_type,day_of_month,start_date,next_occurrence_date) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(),
                        references.userId(),
                        references.categoryId(),
                        "EXPENSE",
                        BigDecimal.ONE,
                        "USD",
                        BigDecimal.ONE,
                        "EXPENSE",
                        32,
                        LocalDate.now(),
                        LocalDate.now()
                )
        ).hasStackTraceContaining("chk_recurring_transactions_day_of_month");
    }

    private String indexDefinition(String index) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'recurring_transactions' AND indexname = ?",
                String.class,
                index
        );
    }

    private References references() {
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO users (id,email,password_hash,display_name,base_currency) VALUES (?,?,?,?,?)",
                userId,
                userId + "@example.test",
                "hash",
                "Test",
                "USD"
        );
        jdbcTemplate.update(
                "INSERT INTO categories (id,user_id,name,transaction_type,icon,color) VALUES (?,?,?,?,?,?)",
                categoryId,
                userId,
                "Food",
                "EXPENSE",
                "icon",
                "#0A1B2C"
        );

        return new References(userId, categoryId);
    }

    private record References(UUID userId, UUID categoryId) {
    }
}

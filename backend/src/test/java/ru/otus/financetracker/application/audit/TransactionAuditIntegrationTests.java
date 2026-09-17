package ru.otus.financetracker.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

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
import ru.otus.financetracker.application.transactions.CreateTransactionCommand;
import ru.otus.financetracker.application.transactions.TransactionService;
import ru.otus.financetracker.application.transactions.UpdateTransactionCommand;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

@Import(TransactionAuditIntegrationTests.FixedClockConfiguration.class)
class TransactionAuditIntegrationTests extends PostgresIntegrationTestSupport {

    private static final Instant AUDIT_TIME = Instant.parse("2026-09-16T12:00:00Z");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Создание, изменение и удаление операции записываются в аудит с безопасными состояниями")
    void shouldRecordCreateUpdateAndDeleteWithSafeBusinessStates() {
        UUID actorUserId = UUID.randomUUID();
        UUID categoryId = insertTransactionReferences(actorUserId);

        var created = transactionService.create(actorUserId, createCommand(categoryId));
        var updated = transactionService.update(
                actorUserId,
                created.id(),
                new UpdateTransactionCommand(
                        created.version(),
                        null,
                        new BigDecimal("22.2200"),
                        "USD",
                        new BigDecimal("1.00000000"),
                        LocalDate.of(2026, 9, 17),
                        " Dinner ",
                        null
                )
        );
        transactionService.delete(actorUserId, updated.id(), updated.version());

        List<AuditRow> auditRows = jdbcTemplate.query(
                "SELECT actor_user_id, action, occurred_at, before_state::text, after_state::text "
                        + "FROM audit_logs WHERE entity_id = ? ORDER BY action",
                (resultSet, rowNumber) -> new AuditRow(
                        resultSet.getObject("actor_user_id", UUID.class),
                        resultSet.getString("action"),
                        resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        resultSet.getString("before_state"),
                        resultSet.getString("after_state")
                ),
                created.id()
        );

        assertThat(auditRows).hasSize(3);

        AuditStateExpectation createdState = new AuditStateExpectation(
                categoryId, "12.3400", "EUR", "1.08500000", "2026-09-16", "Groceries"
        );
        AuditStateExpectation updatedState = new AuditStateExpectation(
                categoryId, "22.2200", "USD", "1.00000000", "2026-09-17", "Dinner"
        );
        assertAuditRow(auditRows.get(0), actorUserId, "CREATE", null, createdState);
        assertAuditRow(auditRows.get(1), actorUserId, "DELETE", updatedState, null);
        assertAuditRow(auditRows.get(2), actorUserId, "UPDATE", createdState, updatedState);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Откат внешней транзакции отменяет операцию и ее запись аудита")
    void shouldRollBackAuditAndTransactionWhenEnclosingTransactionFails() {
        UUID actorUserId = UUID.randomUUID();
        UUID categoryId = insertTransactionReferences(actorUserId);

        assertThatThrownBy(
                () -> transactionTemplate.executeWithoutResult(status -> {
                    transactionService.create(actorUserId, createCommand(categoryId));
                    throw new IllegalStateException("Force enclosing transaction rollback.");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class)).isZero();
    }

    private void assertAuditRow(
            AuditRow auditRow,
            UUID actorUserId,
            String action,
            AuditStateExpectation beforeState,
            AuditStateExpectation afterState
    ) {
        assertThat(auditRow.actorUserId()).isEqualTo(actorUserId);
        assertThat(auditRow.action()).isEqualTo(action);
        assertThat(auditRow.occurredAt()).isEqualTo(AUDIT_TIME);
        assertState(auditRow.beforeState(), beforeState);
        assertState(auditRow.afterState(), afterState);
    }

    private void assertState(String state, AuditStateExpectation expectedState) {
        if (expectedState == null) {
            assertThat(state).isNull();
            return;
        }
        assertThat(state).contains(
                "\"categoryId\": \"" + expectedState.categoryId() + "\"",
                "\"amount\": \"" + expectedState.amount() + "\"",
                "\"currency\": \"" + expectedState.currency() + "\"",
                "\"exchangeRateToBase\": \"" + expectedState.exchangeRateToBase() + "\"",
                "\"transactionDate\": \"" + expectedState.transactionDate() + "\"",
                "\"description\": \"" + expectedState.description() + "\"",
                "\"transactionType\": \"EXPENSE\""
        ).doesNotContain("password", "token", "userId", "createdAt", "updatedAt", "version");
    }

    private UUID insertTransactionReferences(UUID userId) {
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                userId,
                userId + "@example.test",
                "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                "Test user",
                "USD"
        );
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                categoryId,
                userId,
                "Food",
                "EXPENSE",
                "utensils",
                "#0A1B2C"
        );
        return categoryId;
    }

    private CreateTransactionCommand createCommand(UUID categoryId) {
        return new CreateTransactionCommand(
                categoryId,
                new BigDecimal("12.3400"),
                "EUR",
                new BigDecimal("1.08500000"),
                LocalDate.of(2026, 9, 16),
                " Groceries ",
                TransactionType.EXPENSE
        );
    }

    private record AuditRow(
            UUID actorUserId,
            String action,
            Instant occurredAt,
            String beforeState,
            String afterState
    ) {}

    private record AuditStateExpectation(
            UUID categoryId,
            String amount,
            String currency,
            String exchangeRateToBase,
            String transactionDate,
            String description
    ) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(AUDIT_TIME, ZoneOffset.UTC);
        }
    }
}

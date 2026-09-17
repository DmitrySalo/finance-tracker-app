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
import ru.otus.financetracker.application.budgets.BudgetService;
import ru.otus.financetracker.application.budgets.CreateBudgetCommand;
import ru.otus.financetracker.application.budgets.UpdateBudgetCommand;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

@Import(BudgetAuditIntegrationTests.FixedClockConfiguration.class)
class BudgetAuditIntegrationTests extends PostgresIntegrationTestSupport {

    private static final Instant AUDIT_TIME = Instant.parse("2026-09-16T12:00:00Z");

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Создание, изменение и удаление бюджета записываются в аудит")
    void shouldRecordCreateUpdateAndDeleteBudgetStates() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = insertReferences(userId);

        var created = budgetService.create(userId, command(categoryId, "500.0000"));
        var updated = budgetService.update(
                userId,
                created.budget().id(),
                new UpdateBudgetCommand(
                        created.budget().version(),
                        null,
                        null,
                        new BigDecimal("600.0000"),
                        "USD"
                )
        );
        budgetService.delete(userId, updated.budget().id(), updated.budget().version());
        List<AuditRow> rows = jdbcTemplate.query(
                "SELECT actor_user_id, entity_type, action, occurred_at, before_state::text, after_state::text "
                        + "FROM audit_logs WHERE entity_id = ? ORDER BY action",
                (resultSet, rowNumber) -> new AuditRow(
                        resultSet.getObject("actor_user_id", UUID.class),
                        resultSet.getString("entity_type"),
                        resultSet.getString("action"),
                        resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        resultSet.getString("before_state"),
                        resultSet.getString("after_state")
                ),
                created.budget().id()
        );

        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(
                row -> assertThat(row.occurredAt()).isEqualTo(AUDIT_TIME)
        );
        assertThat(rows).allSatisfy(
                row -> assertThat(row.actorUserId()).isEqualTo(userId)
        );
        assertThat(rows).allSatisfy(
                row -> assertThat(row.entityType()).isEqualTo("BUDGET")
        );
        assertThat(rows).extracting(AuditRow::action).containsExactly("CREATE", "DELETE", "UPDATE");
        assertThat(rows.get(0).afterState()).contains(
                "\"limitAmount\": \"500.0000\"",
                "\"budgetMonth\": \"2026-09-01\""
        );
        assertThat(rows.get(1).beforeState())
                .contains("\"limitAmount\": \"600.0000\"")
                .doesNotContain("userId", "version");
        assertThat(rows.get(2).beforeState()).contains("\"limitAmount\": \"500.0000\"");
        assertThat(rows.get(2).afterState()).contains("\"limitAmount\": \"600.0000\"");
    }

    @Test
    @DisplayName("Откат внешней транзакции отменяет бюджет и его запись аудита")
    void shouldRollBackBudgetAndAuditTogether() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = insertReferences(userId);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            budgetService.create(userId, command(categoryId, "500.0000"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM budgets", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class)).isZero();
    }

    private UUID insertReferences(UUID userId) {
        UUID categoryId = UUID.randomUUID();
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

        return categoryId;
    }

    private CreateBudgetCommand command(UUID categoryId, String limit) {
        return new CreateBudgetCommand(
                categoryId,
                LocalDate.of(2026, 9, 1),
                new BigDecimal(limit),
                "USD"
        );
    }

    private record AuditRow(
            UUID actorUserId,
            String entityType,
            String action,
            Instant occurredAt,
            String beforeState,
            String afterState
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

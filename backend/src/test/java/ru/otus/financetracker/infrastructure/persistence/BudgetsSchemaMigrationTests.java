package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

class BudgetsSchemaMigrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Миграция бюджетов создает ограничения и индекс")
    void shouldCreateBudgetConstraintsAndIndex() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'budgets'",
                String.class
        )).contains("id", "user_id", "category_id", "budget_month", "limit_amount", "currency", "version");
        assertThat(indexDefinition("idx_budgets_user_month_category"))
                .contains("user_id, budget_month, category_id");

        var references = references();

        assertThatThrownBy(
                () -> insertBudget(references, LocalDate.of(2026, 9, 2), new BigDecimal("1.0000"))
        ).hasStackTraceContaining("chk_budgets_month_first_day");
        assertThatThrownBy(
                () -> insertBudget(references, LocalDate.of(2026, 9, 1), BigDecimal.ZERO)
        ).hasStackTraceContaining("chk_budgets_limit_amount_positive");
        insertBudget(references, LocalDate.of(2026, 9, 1), new BigDecimal("1.0000"));
        assertThatThrownBy(
                () -> insertBudget(references, LocalDate.of(2026, 9, 1), new BigDecimal("2.0000"))
        ).hasStackTraceContaining("uq_budgets_user_category_month");
        assertThatThrownBy(
                () -> insertBudget(references, LocalDate.of(2026, 10, 1), new BigDecimal("1.0000"), "EUR")
        ).hasStackTraceContaining("fk_budgets_user_base_currency");
    }

    @Test
    @DisplayName("Бюджет отклоняет доходную и чужую категорию")
    void shouldRejectIncomeAndForeignCategories() {
        var userId = insertUser();
        var incomeCategoryId = insertCategory(userId, "INCOME");
        var foreignCategoryId = insertCategory(insertUser(), "EXPENSE");

        assertThatThrownBy(
                () -> insertBudget(
                        new References(userId, incomeCategoryId),
                        LocalDate.of(2026, 9, 1),
                        new BigDecimal("1.0000")
                )
        ).hasStackTraceContaining("fk_budgets_expense_category");
        assertThatThrownBy(
                () -> insertBudget(
                        new References(userId, foreignCategoryId),
                        LocalDate.of(2026, 9, 1),
                        new BigDecimal("1.0000")
                )
        ).hasStackTraceContaining("fk_budgets_expense_category");
    }

    @Test
    @DisplayName("Обновление схемы останавливается при несовпадении валюты бюджета и пользователя")
    void shouldFailUpgradeWhenLegacyBudgetCurrencyDiffersFromUserBaseCurrency() {
        var schema = "budget_currency_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var v7Flyway = Flyway.configure()
                .dataSource(postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .target(MigrationVersion.fromVersion("7"))
                .load();

        v7Flyway.migrate();

        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        jdbcTemplate.execute("SET search_path TO " + schema);
        try {
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
            jdbcTemplate.update(
                    "INSERT INTO budgets (id,user_id,category_id,budget_month,limit_amount,currency) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    userId,
                    categoryId,
                    LocalDate.of(2026, 9, 1),
                    new BigDecimal("1.0000"),
                    "EUR"
            );
            var currentFlyway = Flyway.configure()
                    .dataSource(postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load();

            assertThatThrownBy(currentFlyway::migrate)
                    .hasStackTraceContaining("Cannot enforce budget currency invariant");
        } finally {
            jdbcTemplate.execute("RESET search_path");
            jdbcTemplate.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private String indexDefinition(String index) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'budgets' AND indexname = ?",
                String.class,
                index
        );
    }

    private References references() {
        var userId = insertUser();

        return new References(userId, insertCategory(userId, "EXPENSE"));
    }

    private UUID insertUser() {
        var userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id,email,password_hash,display_name,base_currency) VALUES (?,?,?,?,?)",
                userId,
                userId + "@example.test",
                "hash",
                "Test",
                "USD"
        );

        return userId;
    }

    private UUID insertCategory(UUID userId, String transactionType) {
        var categoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO categories (id,user_id,name,transaction_type,icon,color) VALUES (?,?,?,?,?,?)",
                categoryId,
                userId,
                categoryId.toString(),
                transactionType,
                "icon",
                "#0A1B2C"
        );

        return categoryId;
    }

    private void insertBudget(References references, LocalDate month, BigDecimal limit) {
        insertBudget(references, month, limit, "USD");
    }

    private void insertBudget(References references, LocalDate month, BigDecimal limit, String currency) {
        jdbcTemplate.update(
                "INSERT INTO budgets (id,user_id,category_id,budget_month,limit_amount,currency) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                references.userId(),
                references.categoryId(),
                month,
                limit,
                currency
        );
    }

    private record References(UUID userId, UUID categoryId) {
    }
}

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

@SpringBootTest(properties = {"JWT_ISSUER=https://issuer.test", "JWT_AUDIENCE=finance-tracker-test",
        "JWT_SECRET=test-signing-secret-with-at-least-32-characters", "CORS_ALLOWED_ORIGINS=https://frontend.test",
        "MAX_REQUEST_SIZE=1MB", "MAX_CSV_FILE_SIZE=512KB", "MAX_CSV_ROWS=100", "MAX_REQUEST_HEADER_SIZE=8KB"})
@Testcontainers
class BudgetsSchemaMigrationTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired private JdbcTemplate jdbcTemplate;
    @DynamicPropertySource static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Test void shouldCreateBudgetConstraintsAndIndex() {
        assertThat(jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'budgets'", String.class))
                .contains("id", "user_id", "category_id", "budget_month", "limit_amount", "currency", "version");
        assertThat(indexDefinition("idx_budgets_user_month_category")).contains("user_id, budget_month, category_id");
        var references = references();
        assertThatThrownBy(() -> insertBudget(references, LocalDate.of(2026, 9, 2), new BigDecimal("1.0000")))
                .hasStackTraceContaining("chk_budgets_month_first_day");
        assertThatThrownBy(() -> insertBudget(references, LocalDate.of(2026, 9, 1), BigDecimal.ZERO))
                .hasStackTraceContaining("chk_budgets_limit_amount_positive");
        insertBudget(references, LocalDate.of(2026, 9, 1), new BigDecimal("1.0000"));
        assertThatThrownBy(() -> insertBudget(references, LocalDate.of(2026, 9, 1), new BigDecimal("2.0000")))
                .hasStackTraceContaining("uq_budgets_user_category_month");
    }
    @Test void shouldRejectIncomeAndForeignCategories() {
        UUID userId = insertUser(); UUID income = insertCategory(userId, "INCOME"); UUID foreign = insertCategory(insertUser(), "EXPENSE");
        assertThatThrownBy(() -> insertBudget(new References(userId, income), LocalDate.of(2026, 9, 1), new BigDecimal("1.0000")))
                .hasStackTraceContaining("fk_budgets_expense_category");
        assertThatThrownBy(() -> insertBudget(new References(userId, foreign), LocalDate.of(2026, 9, 1), new BigDecimal("1.0000")))
                .hasStackTraceContaining("fk_budgets_expense_category");
    }
    private String indexDefinition(String index) { return jdbcTemplate.queryForObject("SELECT indexdef FROM pg_indexes WHERE tablename = 'budgets' AND indexname = ?", String.class, index); }
    private References references() { UUID userId = insertUser(); return new References(userId, insertCategory(userId, "EXPENSE")); }
    private UUID insertUser() { UUID id = UUID.randomUUID(); jdbcTemplate.update("INSERT INTO users (id,email,password_hash,display_name,base_currency) VALUES (?,?,?,?,?)", id, id + "@example.test", "hash", "Test", "USD"); return id; }
    private UUID insertCategory(UUID userId, String type) { UUID id = UUID.randomUUID(); jdbcTemplate.update("INSERT INTO categories (id,user_id,name,transaction_type,icon,color) VALUES (?,?,?,?,?,?)", id, userId, id.toString(), type, "icon", "#0A1B2C"); return id; }
    private void insertBudget(References references, LocalDate month, BigDecimal limit) { jdbcTemplate.update("INSERT INTO budgets (id,user_id,category_id,budget_month,limit_amount,currency) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), references.userId(), references.categoryId(), month, limit, "USD"); }
    private record References(UUID userId, UUID categoryId) { }
}

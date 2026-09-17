package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import ru.otus.financetracker.application.budgets.BudgetService;
import ru.otus.financetracker.application.budgets.CreateBudgetCommand;
import ru.otus.financetracker.application.transactions.TransactionRepository;

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
class BudgetExpenseAggregationIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetService budgetService;

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
            jdbcTemplate.execute("TRUNCATE TABLE audit_logs, budgets, transactions, categories, users CASCADE");
        });
    }

    @Test
    void shouldAggregateOnlyCurrentUsersCategoryExpensesWithinBudgetMonthUsingPersistedRates() {
        var userId = insertUser();
        var categoryId = insertCategory(userId, "EXPENSE");
        var anotherCategoryId = insertCategory(userId, "EXPENSE");
        var anotherUserId = insertUser();
        var anotherUserCategoryId = insertCategory(anotherUserId, "EXPENSE");
        insertTransaction(userId, categoryId, "10.0000", "EUR", "1.10000000", LocalDate.of(2026, 9, 1));
        insertTransaction(userId, categoryId, "5.0000", "USD", "1.00000000", LocalDate.of(2026, 9, 30));
        insertTransaction(userId, categoryId, "99.0000", "USD", "1.00000000", LocalDate.of(2026, 8, 31));
        insertTransaction(userId, categoryId, "99.0000", "USD", "1.00000000", LocalDate.of(2026, 10, 1));
        insertTransaction(userId, anotherCategoryId, "99.0000", "USD", "1.00000000", LocalDate.of(2026, 9, 15));
        insertTransaction(anotherUserId, anotherUserCategoryId, "99.0000", "USD", "1.00000000", LocalDate.of(2026, 9, 15));

        var spentAmount = transactionRepository.sumExpenseAmountInBaseCurrency(
                userId, categoryId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)
        );

        assertThat(spentAmount).isEqualByComparingTo("16.000000000000");
    }

    @Test
    void shouldCalculateListedBudgetsUsingBoundedMonthlyAggregations() {
        var userId = insertUser();
        var septemberCategoryId = insertCategory(userId, "EXPENSE");
        var octoberCategoryId = insertCategory(userId, "EXPENSE");
        budgetService.create(
                userId,
                new CreateBudgetCommand(
                        septemberCategoryId,
                        LocalDate.of(2026, 9, 1),
                        new BigDecimal("100.0000"),
                        "USD"
                )
        );
        budgetService.create(
                userId,
                new CreateBudgetCommand(
                        octoberCategoryId,
                        LocalDate.of(2026, 10, 1),
                        new BigDecimal("100.0000"),
                        "USD"
                )
        );
        insertTransaction(userId, septemberCategoryId, "10.0000", "EUR", "1.10000000", LocalDate.of(2026, 9, 15));
        insertTransaction(userId, octoberCategoryId, "10.0000", "EUR", "1.20000000", LocalDate.of(2026, 10, 15));

        var calculations = budgetService.list(userId, PageRequest.of(0, 20)).getContent();

        assertThat(calculations)
                .extracting(
                        calculation -> calculation.budget().budgetMonth(),
                        calculation -> calculation.spentAmount()
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 9, 1), new BigDecimal("11.0000")),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 10, 1), new BigDecimal("12.0000"))
                );
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

    private void insertTransaction(
            UUID userId,
            UUID categoryId,
            String amount,
            String currency,
            String exchangeRate,
            LocalDate transactionDate
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions (id,user_id,category_id,amount,currency,exchange_rate_to_base,transaction_date,transaction_type)
                        VALUES (?,?,?,?,?,?,?,?)
                        """,
                UUID.randomUUID(),
                userId,
                categoryId,
                new BigDecimal(amount),
                currency,
                new BigDecimal(exchangeRate),
                transactionDate,
                "EXPENSE"
        );
    }
}

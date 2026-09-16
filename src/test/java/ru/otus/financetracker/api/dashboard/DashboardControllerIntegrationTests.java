package ru.otus.financetracker.api.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"JWT_ISSUER=https://issuer.test", "JWT_AUDIENCE=finance-tracker-test",
        "JWT_SECRET=test-signing-secret-with-at-least-32-characters", "CORS_ALLOWED_ORIGINS=https://frontend.test",
        "MAX_REQUEST_SIZE=1MB", "MAX_CSV_FILE_SIZE=512KB", "MAX_CSV_ROWS=100", "MAX_REQUEST_HEADER_SIZE=8KB",
        "REGISTRATION_MAX_ATTEMPTS=100"})
@AutoConfigureMockMvc
@Testcontainers
class DashboardControllerIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private MockMvc mockMvc;

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
    void shouldAggregateMonthlyExpensesAndReturnFiveLargestCategories() throws Exception {
        String email = "dashboard-owner@example.test";
        String token = registerAndLogin(email);
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        UUID groceries = insertCategory(userId, "Groceries");
        UUID transport = insertCategory(userId, "Transport");
        UUID income = insertCategory(userId, "Salary");
        insertTransaction(userId, groceries, "10.0000", "1.10000000", LocalDate.of(2026, 9, 1), "EXPENSE");
        insertTransaction(userId, groceries, "5.0000", "1.00000000", LocalDate.of(2026, 9, 30), "EXPENSE");
        insertTransaction(userId, transport, "20.0000", "1.00000000", LocalDate.of(2026, 9, 15), "EXPENSE");
        insertTransaction(userId, income, "1000.0000", "1.00000000", LocalDate.of(2026, 9, 15), "INCOME");
        insertTransaction(userId, transport, "99.0000", "1.00000000", LocalDate.of(2026, 10, 1), "EXPENSE");
        for (int index = 0; index < 9; index++) {
            UUID categoryId = insertCategory(userId, "Category " + index);
            insertTransaction(userId, categoryId, "%d.0000".formatted(index == 8 ? 6 : index + 1), "1.00000000",
                    LocalDate.of(2026, 9, 10), "EXPENSE");
        }
        UUID firstTieCategoryId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondTieCategoryId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        insertCategory(firstTieCategoryId, userId, "Tie first");
        insertCategory(secondTieCategoryId, userId, "Tie second");
        insertTransaction(userId, firstTieCategoryId, "9.0000", "1.00000000", LocalDate.of(2026, 9, 10), "EXPENSE");
        insertTransaction(userId, secondTieCategoryId, "9.0000", "1.00000000", LocalDate.of(2026, 9, 10), "EXPENSE");
        UUID otherUserId = insertUser();
        UUID otherUserCategoryId = insertCategory(otherUserId, "Other user category");
        insertTransaction(otherUserId, otherUserCategoryId, "999.0000", "1.00000000", LocalDate.of(2026, 9, 15), "EXPENSE");

        mockMvc.perform(get("/api/v1/dashboard?month=2026-09").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.expensesByCategory.length()").value(10))
                .andExpect(jsonPath("$.expensesByCategory[0].categoryName").value("Transport"))
                .andExpect(jsonPath("$.expensesByCategory[0].amount").value("20.000000000000"))
                .andExpect(jsonPath("$.expensesByCategory[1].categoryName").value("Groceries"))
                .andExpect(jsonPath("$.expensesByCategory[1].amount").value("16.000000000000"))
                .andExpect(jsonPath("$.topExpenseCategories.length()").value(5))
                .andExpect(jsonPath("$.topExpenseCategories[0].categoryName").value("Transport"))
                .andExpect(jsonPath("$.topExpenseCategories[0].amount").value("20.000000000000"))
                .andExpect(jsonPath("$.topExpenseCategories[1].categoryName").value("Groceries"))
                .andExpect(jsonPath("$.topExpenseCategories[1].amount").value("16.000000000000"))
                .andExpect(jsonPath("$.topExpenseCategories[2].categoryName").value("Tie first"))
                .andExpect(jsonPath("$.topExpenseCategories[2].amount").value("9.000000000000"))
                .andExpect(jsonPath("$.topExpenseCategories[3].categoryName").value("Tie second"))
                .andExpect(jsonPath("$.topExpenseCategories[3].amount").value("9.000000000000"))
                .andExpect(jsonPath("$.topExpenseCategories[4].categoryName").value("Category 7"))
                .andExpect(jsonPath("$.topExpenseCategories[4].amount").value("8.000000000000"));
    }

    @Test
    void shouldReturnSixConsecutiveMonthsAndExcludeIncomeFromSpendingTrend() throws Exception {
        String email = "trend-owner@example.test";
        String token = registerAndLogin(email);
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        UUID categoryId = insertCategory(userId, "Travel");
        UUID incomeCategoryId = insertCategory(userId, "Salary");
        insertTransaction(userId, categoryId, "10.0000", "1.50000000", LocalDate.of(2026, 4, 1), "EXPENSE");
        insertTransaction(userId, categoryId, "20.0000", "1.00000000", LocalDate.of(2026, 9, 30), "EXPENSE");
        insertTransaction(userId, incomeCategoryId, "500.0000", "1.00000000", LocalDate.of(2026, 9, 30), "INCOME");
        insertTransaction(userId, categoryId, "99.0000", "1.00000000", LocalDate.of(2026, 3, 31), "EXPENSE");
        UUID otherUserId = insertUser();
        UUID otherUserCategoryId = insertCategory(otherUserId, "Other user trend");
        insertTransaction(otherUserId, otherUserCategoryId, "999.0000", "1.00000000", LocalDate.of(2026, 9, 30), "EXPENSE");

        mockMvc.perform(get("/api/v1/dashboard/spending-trend?endMonth=2026-09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endMonth").value("2026-09"))
                .andExpect(jsonPath("$.months.length()").value(6))
                .andExpect(jsonPath("$.months[0].month").value("2026-04-01"))
                .andExpect(jsonPath("$.months[0].amount").value("15.000000000000"))
                .andExpect(jsonPath("$.months[1].amount").value("0"))
                .andExpect(jsonPath("$.months[5].month").value("2026-09-01"))
                .andExpect(jsonPath("$.months[5].amount").value("20.000000000000"));
    }

    @Test
    void shouldRequireAuthenticationAndValidateMonthParameters() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard?month=2026-09")).andExpect(status().isUnauthorized());

        String token = registerAndLogin("dashboard-validation@example.test");
        mockMvc.perform(get("/api/v1/dashboard?month=2026-13").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("month"));
        mockMvc.perform(get("/api/v1/dashboard/spending-trend?endMonth=2026-9")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("endMonth"));
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("month"));
        mockMvc.perform(get("/api/v1/dashboard/spending-trend").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("endMonth"));
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash,display_name,base_currency) VALUES (?,?,?,?,?)",
                id, id + "@example.test", "hash", "Test", "USD");
        return id;
    }

    private UUID insertCategory(UUID userId, String name) {
        return insertCategory(UUID.randomUUID(), userId, name);
    }

    private UUID insertCategory(UUID id, UUID userId, String name) {
        String transactionType = name.equals("Salary") ? "INCOME" : "EXPENSE";
        jdbcTemplate.update("INSERT INTO categories (id,user_id,name,transaction_type,icon,color) VALUES (?,?,?,?,?,?)",
                id, userId, name, transactionType, "icon", "#0A1B2C");
        return id;
    }

    private void insertTransaction(UUID userId, UUID categoryId, String amount, String exchangeRate,
                                   LocalDate transactionDate, String transactionType) {
        jdbcTemplate.update("""
                        INSERT INTO transactions (id,user_id,category_id,amount,currency,exchange_rate_to_base,transaction_date,transaction_type)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, UUID.randomUUID(), userId, categoryId, new BigDecimal(amount), "USD",
                new BigDecimal(exchangeRate), transactionDate, transactionType);
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"a-secure-password\",\"displayName\":\"Test User\",\"baseCurrency\":\"USD\"}".formatted(email)))
                .andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"a-secure-password\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new tools.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}

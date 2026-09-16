package ru.otus.financetracker.api.transactions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
        "MAX_REQUEST_HEADER_SIZE=8KB",
        "REGISTRATION_MAX_ATTEMPTS=100"
})
@AutoConfigureMockMvc
@Testcontainers
class TransactionControllerIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldCreateAndGetTransactionWithPersistedExchangeRate() throws Exception {
        String token = registerAndLogin("owner@example.test");
        String categoryId = createCategory(token, "EXPENSE");

        var createResponse = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(transactionRequest(categoryId, "EXPENSE")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/v1/transactions/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.exchangeRateToBase").value(1.085))
                .andExpect(jsonPath("$.transactionType").value("EXPENSE"))
                .andReturn();
        String transactionId = new tools.jackson.databind.ObjectMapper().readTree(createResponse.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.exchangeRateToBase").value(1.085));
    }

    @Test
    void shouldRejectMismatchedAndForeignCategoriesAndHideAnotherUsersTransaction() throws Exception {
        String ownerToken = registerAndLogin("owner@example.test");
        String otherToken = registerAndLogin("other@example.test");
        String ownerCategoryId = createCategory(ownerToken, "EXPENSE");
        String incomeCategoryId = createCategory(ownerToken, "INCOME");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(transactionRequest(incomeCategoryId, "EXPENSE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType("application/json")
                        .content(transactionRequest(ownerCategoryId, "EXPENSE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        var response = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(transactionRequest(ownerCategoryId, "EXPENSE")))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = new tools.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldFilterTransactionsByDateRange() throws Exception {
        String token = registerAndLogin("date-filter@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        createTransaction(token, categoryId, "10.00", "2026-09-10", "EXPENSE", "before");
        String matchingId = createTransaction(token, categoryId, "20.00", "2026-09-15", "EXPENSE", "matching");
        createTransaction(token, categoryId, "30.00", "2026-09-20", "EXPENSE", "after");

        mockMvc.perform(get("/api/v1/transactions?fromDate=2026-09-15&toDate=2026-09-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matchingId))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void shouldFilterTransactionsByCategoryAndHideForeignCategory() throws Exception {
        String ownerToken = registerAndLogin("category-filter-owner@example.test");
        String otherToken = registerAndLogin("category-filter-other@example.test");
        String categoryId = createCategory(ownerToken, "EXPENSE");
        String otherCategoryId = createCategory(ownerToken, "EXPENSE");
        String foreignCategoryId = createCategory(otherToken, "EXPENSE");
        String matchingId = createTransaction(ownerToken, categoryId, "10.00", "2026-09-15", "EXPENSE", "matching");
        createTransaction(ownerToken, otherCategoryId, "10.00", "2026-09-15", "EXPENSE", "other category");

        mockMvc.perform(get("/api/v1/transactions?categoryId={categoryId}", categoryId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matchingId));
        mockMvc.perform(get("/api/v1/transactions?categoryId={categoryId}", foreignCategoryId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldFilterTransactionsByAmountRange() throws Exception {
        String token = registerAndLogin("amount-filter@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        createTransaction(token, categoryId, "9.99", "2026-09-15", "EXPENSE", "below");
        String matchingId = createTransaction(token, categoryId, "15.00", "2026-09-15", "EXPENSE", "matching");
        createTransaction(token, categoryId, "20.01", "2026-09-15", "EXPENSE", "above");

        mockMvc.perform(get("/api/v1/transactions?minAmount=10.00&maxAmount=20.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matchingId));
    }

    @Test
    void shouldFilterTransactionsByType() throws Exception {
        String token = registerAndLogin("type-filter@example.test");
        String expenseCategoryId = createCategory(token, "EXPENSE");
        String incomeCategoryId = createCategory(token, "INCOME");
        String matchingId = createTransaction(token, expenseCategoryId, "10.00", "2026-09-15", "EXPENSE", "expense");
        createTransaction(token, incomeCategoryId, "10.00", "2026-09-15", "INCOME", "income");

        mockMvc.perform(get("/api/v1/transactions?transactionType=EXPENSE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matchingId));
    }

    @Test
    void shouldCombineTransactionFiltersAndKeepCurrentUserIsolation() throws Exception {
        String ownerToken = registerAndLogin("combined-owner@example.test");
        String otherToken = registerAndLogin("combined-other@example.test");
        String expenseCategoryId = createCategory(ownerToken, "EXPENSE");
        String incomeCategoryId = createCategory(ownerToken, "INCOME");
        createTransaction(otherToken, createCategory(otherToken, "EXPENSE"), "15.00", "2026-09-15", "EXPENSE", "foreign");
        String matchingId = createTransaction(ownerToken, expenseCategoryId, "15.00", "2026-09-15", "EXPENSE", "matching");
        createTransaction(ownerToken, expenseCategoryId, "25.00", "2026-09-15", "EXPENSE", "too much");
        createTransaction(ownerToken, incomeCategoryId, "15.00", "2026-09-15", "INCOME", "wrong type");

        mockMvc.perform(get("/api/v1/transactions?fromDate=2026-09-15&toDate=2026-09-15&categoryId={categoryId}"
                                + "&minAmount=10.00&maxAmount=20.00&transactionType=EXPENSE", expenseCategoryId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matchingId));
    }

    @Test
    void shouldUseStableTransactionSortAndLimitPageSize() throws Exception {
        String token = registerAndLogin("sort-filter@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        String firstId = createTransaction(token, categoryId, "10.00", "2026-09-15", "EXPENSE", "first");
        String secondId = createTransaction(token, categoryId, "10.00", "2026-09-15", "EXPENSE", "second");

        var firstResponse = mockMvc.perform(get("/api/v1/transactions?sort=amount,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        var secondResponse = mockMvc.perform(get("/api/v1/transactions?sort=amount,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var objectMapper = new tools.jackson.databind.ObjectMapper();
        var firstOrder = objectMapper.readTree(firstResponse.getResponse().getContentAsString()).get("items");
        var secondOrder = objectMapper.readTree(secondResponse.getResponse().getContentAsString()).get("items");

        org.assertj.core.api.Assertions.assertThat(firstOrder).isEqualTo(secondOrder);
        var orderedIds = firstOrder;
        org.assertj.core.api.Assertions.assertThat(orderedIds.get(0).get("id").asText())
                .isEqualTo(java.util.List.of(firstId, secondId).stream().sorted().findFirst().orElseThrow());
        org.assertj.core.api.Assertions.assertThat(orderedIds.get(1).get("id").asText())
                .isEqualTo(java.util.List.of(firstId, secondId).stream().sorted().skip(1).findFirst().orElseThrow());
        mockMvc.perform(get("/api/v1/transactions?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectInvalidTransactionRangesAndSort() throws Exception {
        String token = registerAndLogin("invalid-filter@example.test");

        mockMvc.perform(get("/api/v1/transactions?fromDate=2026-09-16&toDate=2026-09-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/transactions?minAmount=20.00&maxAmount=10.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/transactions?sort=userId,asc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String createCategory(String token, String transactionType) throws Exception {
        var response = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Food %s %s","transactionType":"%s","icon":"utensils","color":"#0A1B2C"}
                                """.formatted(transactionType, java.util.UUID.randomUUID(), transactionType)))
                .andExpect(status().isCreated())
                .andReturn();
        return new tools.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asText();
    }

    private String transactionRequest(String categoryId, String transactionType) {
        return """
                {"categoryId":"%s","amount":12.3400,"currency":"EUR","exchangeRateToBase":1.08500000,
                "transactionDate":"2026-09-16","description":"Groceries","transactionType":"%s"}
                """.formatted(categoryId, transactionType);
    }

    private String createTransaction(String token, String categoryId, String amount, String transactionDate,
                                     String transactionType, String description) throws Exception {
        var response = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"categoryId":"%s","amount":%s,"currency":"EUR","exchangeRateToBase":1.08500000,
                                "transactionDate":"%s","description":"%s","transactionType":"%s"}
                                """.formatted(categoryId, amount, transactionDate, description, transactionType)))
                .andExpect(status().isCreated())
                .andReturn();
        return new tools.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asText();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"a-secure-password","displayName":"Test User","baseCurrency":"USD"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"a-secure-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new tools.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}

package ru.otus.financetracker.api.transactions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
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
        "MAX_CSV_ROWS=101",
        "MAX_REQUEST_HEADER_SIZE=8KB",
        "REGISTRATION_MAX_ATTEMPTS=100"
})
@AutoConfigureMockMvc
@Testcontainers
class TransactionControllerIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

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
            jdbcTemplate.execute("TRUNCATE TABLE audit_logs, transactions, categories, users CASCADE");
        });
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
    void shouldUpdateTransactionWithOwnedMatchingCategory() throws Exception {
        String token = registerAndLogin("update-owner@example.test");
        String expenseCategoryId = createCategory(token, "EXPENSE");
        String incomeCategoryId = createCategory(token, "INCOME");
        String transactionId = createTransaction(token, expenseCategoryId, "12.34", "2026-09-16", "EXPENSE", "Groceries");

        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"version":0,"categoryId":"%s","amount":22.2200,"currency":"USD",
                                "exchangeRateToBase":1.00000000,"transactionDate":"2026-09-17",
                                "description":" Dinner ","transactionType":"INCOME"}
                                """.formatted(incomeCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(incomeCategoryId))
                .andExpect(jsonPath("$.amount").value(22.22))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.transactionDate").value("2026-09-17"))
                .andExpect(jsonPath("$.description").value("Dinner"))
                .andExpect(jsonPath("$.transactionType").value("INCOME"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void shouldDeleteOwnedTransaction() throws Exception {
        String token = registerAndLogin("delete-owner@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        String transactionId = createTransaction(token, categoryId, "12.34", "2026-09-16", "EXPENSE", "Groceries");

        mockMvc.perform(delete("/api/v1/transactions/{transactionId}?version=0", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldRejectInvalidAndStaleTransactionUpdates() throws Exception {
        String token = registerAndLogin("invalid-update-owner@example.test");
        String expenseCategoryId = createCategory(token, "EXPENSE");
        String transactionId = createTransaction(token, expenseCategoryId, "12.34", "2026-09-16", "EXPENSE", "Groceries");

        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"version\":0,\"amount\":0,\"currency\":\"usd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"version\":0,\"description\":\"Updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"version\":0,\"description\":\"Stale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void shouldRejectStaleTransactionDeletionAndPreserveTransaction() throws Exception {
        String token = registerAndLogin("stale-delete-owner@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        String transactionId = createTransaction(token, categoryId, "12.34", "2026-09-16", "EXPENSE", "Groceries");

        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"version\":0,\"description\":\"Updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}?version=0", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void shouldRejectMismatchedOrForeignCategoryOnTransactionUpdate() throws Exception {
        String ownerToken = registerAndLogin("category-update-owner@example.test");
        String otherToken = registerAndLogin("category-update-other@example.test");
        String expenseCategoryId = createCategory(ownerToken, "EXPENSE");
        String incomeCategoryId = createCategory(ownerToken, "INCOME");
        String foreignCategoryId = createCategory(otherToken, "EXPENSE");
        String transactionId = createTransaction(ownerToken, expenseCategoryId, "12.34", "2026-09-16", "EXPENSE", "Groceries");

        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"version":0,"categoryId":"%s","transactionType":"EXPENSE"}
                                """.formatted(incomeCategoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"version":0,"categoryId":"%s"}
                                """.formatted(foreignCategoryId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldHideForeignTransactionForUpdateAndDelete() throws Exception {
        String ownerToken = registerAndLogin("mutation-owner@example.test");
        String otherToken = registerAndLogin("mutation-other@example.test");
        String transactionId = createTransaction(ownerToken, createCategory(ownerToken, "EXPENSE"), "12.34", "2026-09-16",
                "EXPENSE", "Groceries");

        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType("application/json")
                        .content("{\"version\":0,\"description\":\"Attempt\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}?version=0", transactionId)
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
        mockMvc.perform(
            get("/api/v1/transactions?size=101")
                .header("Authorization", "Bearer " + token)
        )
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
        mockMvc.perform(
            get("/api/v1/transactions?sort=userId,asc")
                .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldExportFilteredTransactionsAsProtectedCsvForCurrentUserOnly() throws Exception {
        String ownerToken = registerAndLogin("export-owner@example.test");
        String otherToken = registerAndLogin("export-other@example.test");
        String ownerCategoryId = createCategory(ownerToken, "EXPENSE");
        String otherCategoryId = createCategory(otherToken, "EXPENSE");
        createTransaction(ownerToken, ownerCategoryId, "10.00", "2026-09-14", "EXPENSE", "excluded");
        String transactionId = createTransaction(ownerToken, ownerCategoryId, "12.34", "2026-09-15", "EXPENSE", "formula");
        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"version\":0,\"description\":\"=SUM(A1:A2), \\\"quoted\\\"\"}"))
                .andExpect(status().isOk());
        createTransaction(otherToken, otherCategoryId, "12.34", "2026-09-15", "EXPENSE", "foreign");

        var response = export("/api/v1/transactions/export?fromDate=2026-09-15&toDate=2026-09-15&minAmount=12.00", ownerToken)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=transactions.csv"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv;charset=UTF-8")))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(response.getResponse().getContentAsString())
                .isEqualTo("categoryId,amount,currency,exchangeRateToBase,transactionDate,description,transactionType\r\n"
                        + ownerCategoryId + ",12.3400,EUR,1.08500000,2026-09-15,\"'=SUM(A1:A2), \"\"quoted\"\"\",EXPENSE\r\n");
    }

    @Test
    void shouldLimitExportToConfiguredMaximumRows() throws Exception {
        String token = registerAndLogin("export-limit@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        for (int index = 0; index < 102; index++) {
            createTransaction(token, categoryId, "10.00", "2026-09-15", "EXPENSE", "transaction-" + index);
        }

        var response = export("/api/v1/transactions/export", token)
                .andExpect(status().isOk())
                .andReturn();

        String[] lines = response.getResponse().getContentAsString().split("\\r\\n");
        org.assertj.core.api.Assertions.assertThat(lines).hasSize(102);
        org.assertj.core.api.Assertions.assertThat(java.util.Arrays.stream(lines)
                        .skip(1)
                        .map(line -> line.split(",")[5]))
                .doesNotHaveDuplicates()
                .allSatisfy(description -> org.assertj.core.api.Assertions.assertThat(description)
                        .matches("transaction-(?:[0-9]|[1-9][0-9]|10[0-1])"));
    }

    @Test
    void shouldRejectUnauthenticatedAndInvalidOrForeignExportFilters() throws Exception {
        String ownerToken = registerAndLogin("export-filter-owner@example.test");
        String otherToken = registerAndLogin("export-filter-other@example.test");
        String foreignCategoryId = createCategory(otherToken, "EXPENSE");

        mockMvc.perform(get("/api/v1/transactions/export"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transactions/export?fromDate=2026-09-16&toDate=2026-09-15")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/transactions/export?minAmount=20.00&maxAmount=10.00")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/transactions/export?categoryId={categoryId}", foreignCategoryId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldProtectFormulaAfterLeadingWhitespaceInExport() throws Exception {
        String token = registerAndLogin("export-formula@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        String transactionId = createTransaction(token, categoryId, "12.34", "2026-09-15", "EXPENSE", "formula");
        mockMvc.perform(patch("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"version\":0,\"description\":\"\\t=HYPERLINK(\\\"https://example.test\\\")\"}"))
                .andExpect(status().isOk());

        var response = export("/api/v1/transactions/export", token)
                .andExpect(status().isOk())
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(response.getResponse().getContentAsString())
                .contains("\"'=HYPERLINK(\"\"https://example.test\"\")\"");
    }

    @Test
    void shouldPreviewMappedCsvWithoutPersistingTransactions() throws Exception {
        String token = registerAndLogin("import-preview@example.test");
        String categoryId = createCategory(token, "EXPENSE");

        previewRequest(token, "category,amount,currency,rate,date,note,type\r\n"
                        + categoryId + ",12.3400,EUR,1.08500000,2026-09-16,\"Dinner, home\",EXPENSE\r\n",
                        validImportMapping())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].lineNumber").value(2))
                .andExpect(jsonPath("$.rows[0].description").value("Dinner, home"))
                .andExpect(jsonPath("$.lineErrors.length()").value(0));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Integer.class))
                .isZero();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Integer.class))
                .isZero();
    }

    @Test
    void shouldNotPersistAnyTransactionsWhenConfirmedCsvHasAnInvalidRow() throws Exception {
        String token = registerAndLogin("import-confirm-invalid@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        String csv = "category,amount,currency,rate,date,note,type\n"
                + categoryId + ",12.3400,EUR,1.08500000,2026-09-16,First,EXPENSE\n"
                + categoryId + ",invalid,EUR,1.08500000,2026-09-17,Second,EXPENSE\n";

        confirmRequest(token, csv, validImportMapping())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("rows[3].amount"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Integer.class))
                .isZero();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Integer.class))
                .isZero();
    }

    @Test
    void shouldPersistEveryTransactionAndAuditRecordWhenConfirmedCsvIsValid() throws Exception {
        String token = registerAndLogin("import-confirm-valid@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        String csv = "category,amount,currency,rate,date,note,type\n"
                + categoryId + ",12.3400,EUR,1.08500000,2026-09-16,First,EXPENSE\n"
                + categoryId + ",23.4500,USD,1.00000000,2026-09-17,Second,EXPENSE\n";

        confirmRequest(token, csv, validImportMapping())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(2));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Integer.class))
                .isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE entity_type = 'TRANSACTION' AND action = 'CREATE'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void shouldRejectUnknownImportColumnMapping() throws Exception {
        String token = registerAndLogin("import-mapping@example.test");

        previewRequest(token, "amount\r\n12.3400\r\n", "{\"columns\":{\"unknown\":\"amount\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("mapping"));
    }

    @Test
    void shouldReportInvalidAmountDateAndCurrencyAsLineErrors() throws Exception {
        String token = registerAndLogin("import-invalid-values@example.test");
        String categoryId = createCategory(token, "EXPENSE");

        previewRequest(token, "category,amount,currency,rate,date,note,type\n"
                        + categoryId + ",invalid,eur,1.08500000,16-09-2026,Dinner,EXPENSE\n", validImportMapping())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineErrors.length()").value(3))
                .andExpect(jsonPath("$.lineErrors[0].lineNumber").value(2))
                .andExpect(jsonPath("$.lineErrors[0].field").value("amount"))
                .andExpect(jsonPath("$.lineErrors[1].field").value("currency"))
                .andExpect(jsonPath("$.lineErrors[2].field").value("transactionDate"));
    }

    @Test
    void shouldRejectCsvImportPreviewAboveConfiguredRowLimit() throws Exception {
        String token = registerAndLogin("import-limit@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        StringBuilder csv = new StringBuilder("category,amount,currency,rate,date,note,type\n");
        for (int index = 0; index < 102; index++) {
            csv.append(categoryId).append(",1.00,EUR,1.00000000,2026-09-16,note,EXPENSE\n");
        }

        previewRequest(token, csv.toString(), validImportMapping())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("file"))
                .andExpect(jsonPath("$.violations[0].code").value("MAX_ROWS"));
    }

    @Test
    void shouldRequireAuthenticationForCsvImportPreview() throws Exception {
        previewRequest(null, "category,amount,currency,rate,date,type\n", validImportMapping())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectMissingCsvImportPreviewParts() throws Exception {
        String token = registerAndLogin("import-missing-parts@example.test");

        mockMvc.perform(multipart("/api/v1/transactions/imports/preview")
                        .file(new MockMultipartFile("file", "transactions.csv", "text/csv", "header\n".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("mapping"))
                .andExpect(jsonPath("$.violations[0].code").value("REQUIRED"));
        mockMvc.perform(multipart("/api/v1/transactions/imports/preview")
                        .file(new MockMultipartFile("mapping", "", "application/json",
                                validImportMapping().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("file"))
                .andExpect(jsonPath("$.violations[0].code").value("REQUIRED"));
    }

    @Test
    void shouldRejectCsvImportPreviewFileAboveMultipartLimit() throws Exception {
        String token = registerAndLogin("import-file-limit@example.test");
        byte[] oversizedCsv = new byte[512 * 1024 + 1];
        java.util.Arrays.fill(oversizedCsv, (byte) 'a');

        mockMvc.perform(multipart("/api/v1/transactions/imports/preview")
                        .file(new MockMultipartFile("file", "transactions.csv", "text/csv", oversizedCsv))
                        .file(new MockMultipartFile("mapping", "", "application/json",
                                validImportMapping().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    private org.springframework.test.web.servlet.ResultActions export(String url, String token) throws Exception {
        var result = mockMvc.perform(
            get(url).header("Authorization", "Bearer " + token)
        )
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(result));
    }

    private org.springframework.test.web.servlet.ResultActions previewRequest(String token, String csv, String mapping) throws Exception {
        return importRequest("/api/v1/transactions/imports/preview", token, csv, mapping);
    }

    private org.springframework.test.web.servlet.ResultActions confirmRequest(String token, String csv, String mapping) throws Exception {
        return importRequest("/api/v1/transactions/imports", token, csv, mapping);
    }

    private org.springframework.test.web.servlet.ResultActions importRequest(String url, String token, String csv, String mapping) throws Exception {
        var request = multipart(url)
                .file(new MockMultipartFile("file", "transactions.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("mapping", "", "application/json", mapping.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private String validImportMapping() {
        return """
                {"columns":{"categoryId":"category","amount":"amount","currency":"currency",
                "exchangeRateToBase":"rate","transactionDate":"date","description":"note","transactionType":"type"}}
                """;
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

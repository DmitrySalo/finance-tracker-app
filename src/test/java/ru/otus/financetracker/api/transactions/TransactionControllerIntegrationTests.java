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

    private String createCategory(String token, String transactionType) throws Exception {
        var response = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Food %s","transactionType":"%s","icon":"utensils","color":"#0A1B2C"}
                                """.formatted(transactionType, transactionType)))
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

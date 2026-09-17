package ru.otus.financetracker.api.recurring;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"JWT_ISSUER=https://issuer.test", "JWT_AUDIENCE=finance-tracker-test", "JWT_SECRET=test-signing-secret-with-at-least-32-characters", "CORS_ALLOWED_ORIGINS=https://frontend.test", "MAX_REQUEST_SIZE=1MB", "MAX_CSV_FILE_SIZE=512KB", "MAX_CSV_ROWS=100", "MAX_REQUEST_HEADER_SIZE=8KB", "REGISTRATION_MAX_ATTEMPTS=100"})
@AutoConfigureMockMvc
@Testcontainers
class RecurringTransactionControllerIntegrationTests {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired private MockMvc mockMvc; @Autowired private JdbcTemplate jdbcTemplate; @Autowired private TransactionTemplate transactionTemplate;
    @DynamicPropertySource static void configureDataSource(DynamicPropertyRegistry registry) { registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername); registry.add("spring.datasource.password", POSTGRES::getPassword); }
    @AfterEach void clearData() { transactionTemplate.executeWithoutResult(status -> { jdbcTemplate.execute("SET LOCAL session_replication_role = replica"); jdbcTemplate.execute("TRUNCATE TABLE recurring_transaction_occurrences, recurring_transactions, audit_logs, budgets, transactions, categories, users CASCADE"); }); }
    @Test void shouldCreateAndListOwnedRecurringRule() throws Exception {
        String token = registerAndLogin("recurring-owner@example.test"); String categoryId = createCategory(token); String body = request(categoryId);
        var result = mockMvc.perform(post("/api/v1/recurring-transactions").header("Authorization", "Bearer " + token).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.dayOfMonth").value(31)).andExpect(jsonPath("$.nextOccurrenceDate").value("2026-02-28")).andReturn();
        String id = new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/v1/recurring-transactions/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(get("/api/v1/recurring-transactions").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        mockMvc.perform(patch("/api/v1/recurring-transactions/{id}", id).header("Authorization", "Bearer " + token).contentType("application/json").content("{\"version\":0,\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(delete("/api/v1/recurring-transactions/{id}?version=1", id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
    }
    @Test void shouldRequireAuthenticationAndRejectForeignCategory() throws Exception {
        mockMvc.perform(get("/api/v1/recurring-transactions")).andExpect(status().isUnauthorized());
        String owner = registerAndLogin("recurring-owner2@example.test"); String other = registerAndLogin("recurring-other@example.test");
        mockMvc.perform(post("/api/v1/recurring-transactions").header("Authorization", "Bearer " + other).contentType("application/json").content(request(createCategory(owner)))).andExpect(status().isNotFound());
    }
    private String request(String categoryId) { return "{\"categoryId\":\"%s\",\"amount\":10.0000,\"currency\":\"USD\",\"exchangeRateToBase\":1.00000000,\"description\":\"Rent\",\"transactionType\":\"EXPENSE\",\"dayOfMonth\":31,\"startDate\":\"2026-02-01\",\"active\":true}".formatted(categoryId); }
    private String createCategory(String token) throws Exception { var result = mockMvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token).contentType("application/json").content("{\"name\":\"Rent %s\",\"transactionType\":\"EXPENSE\",\"icon\":\"home\",\"color\":\"#0A1B2C\"}".formatted(java.util.UUID.randomUUID()))).andExpect(status().isCreated()).andReturn(); return new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText(); }
    private String registerAndLogin(String email) throws Exception { mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"email\":\"%s\",\"password\":\"a-secure-password\",\"displayName\":\"Test User\",\"baseCurrency\":\"USD\"}".formatted(email))).andExpect(status().isCreated()); var result = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"%s\",\"password\":\"a-secure-password\"}".formatted(email))).andExpect(status().isOk()).andReturn(); return new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("accessToken").asText(); }
}

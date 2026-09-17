package ru.otus.financetracker.api.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AuditLogControllerIntegrationTests {
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
            jdbcTemplate.execute("TRUNCATE TABLE audit_logs, budgets, transactions, categories, users CASCADE");
        });
    }

    @Test
    void shouldListOnlyCurrentUsersFilteredAuditEntries() throws Exception {
        String ownerToken = registerAndLogin("audit-owner@example.test");
        UUID ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                UUID.class,
                "audit-owner@example.test"
        );
        UUID entityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO audit_logs (id, actor_user_id, entity_type, entity_id, action, occurred_at, before_state, after_state) VALUES (?, ?, 'BUDGET', ?, 'UPDATE', now(), '{\"limitAmount\":\"100.0000\"}', '{\"limitAmount\":\"120.0000\"}')",
                UUID.randomUUID(),
                ownerId,
                entityId
        );
        UUID otherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency, created_at, updated_at, version) VALUES (?, ?, 'x', 'Other', 'USD', now(), now(), 0)",
                otherId,
                "audit-other@example.test"
        );
        jdbcTemplate.update(
                "INSERT INTO audit_logs (id, actor_user_id, entity_type, entity_id, action, occurred_at, after_state) VALUES (?, ?, 'BUDGET', ?, 'CREATE', now(), '{}')",
                UUID.randomUUID(),
                otherId,
                entityId
        );

        mockMvc.perform(
                get("/api/v1/audit-logs?entityType=BUDGET&entityId={entityId}&page=0&size=20", entityId)
                        .header("Authorization", "Bearer " + ownerToken)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].beforeState.limitAmount").value("100.0000"))
                .andExpect(jsonPath("$.items[0].afterState.limitAmount").value("120.0000"));
        mockMvc.perform(get("/api/v1/audit-logs?entityType=USER").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/audit-logs?entityType=BUDGET"))
            .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"a-secure-password\",\"displayName\":\"Audit User\",\"baseCurrency\":\"USD\"}".formatted(email)))
            .andExpect(status().isCreated());
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"a-secure-password\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
        return new tools.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
    }
}

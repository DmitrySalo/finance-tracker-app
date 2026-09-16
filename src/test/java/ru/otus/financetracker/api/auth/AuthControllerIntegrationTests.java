package ru.otus.financetracker.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        "MAX_REQUEST_HEADER_SIZE=8KB"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTests {

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
    void clearUsers() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldRegisterUserAndStoreBcryptHash() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"Person@Example.Test","password":"a-secure-password","displayName":"Test User","baseCurrency":"USD"}
                                """))
                .andExpect(status().isCreated());

        var user = jdbcTemplate.queryForMap(
                "SELECT email, password_hash, display_name, base_currency FROM users WHERE email = ?", "person@example.test"
        );
        assertThat(user).containsEntry("email", "person@example.test")
                .containsEntry("display_name", "Test User")
                .containsEntry("base_currency", "USD");
        assertThat((String) user.get("password_hash")).startsWith("$2").isNotEqualTo("a-secure-password");
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"person@example.test","password":"short","displayName":"Test User","baseCurrency":"USD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    @Test
    void shouldReturnSameSuccessfulStatusForDuplicateEmail() throws Exception {
        var request = """
                {"email":"person@example.test","password":"a-secure-password","displayName":"Test User","baseCurrency":"USD"}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(request))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
    }
}

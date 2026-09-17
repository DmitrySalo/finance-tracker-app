package ru.otus.financetracker.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
class AuthControllerIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

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
                "SELECT email, password_hash, display_name, base_currency FROM users WHERE email = ?",
                "person@example.test"
        );
        assertThat(user)
            .containsEntry("email", "person@example.test")
            .containsEntry("display_name", "Test User")
            .containsEntry("base_currency", "USD");
        assertThat((String) user.get("password_hash"))
            .startsWith("$2")
            .isNotEqualTo("a-secure-password");
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

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(request))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldIssueJwtAndReturnCurrentUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"person@example.test","password":"a-secure-password","displayName":"Test User","baseCurrency":"USD"}
                                """))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"Person@Example.Test","password":"a-secure-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        String token = new tools.jackson.databind.ObjectMapper()
            .readTree(login.getResponse().getContentAsString())
            .get("accessToken")
            .asString();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person@example.test"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.baseCurrency").value("USD"));
    }

    @Test
    void shouldRejectProtectedEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectLoginWithInvalidCredentialsWithoutDisclosingAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"unknown@example.test","password":"a-secure-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void shouldRejectJwtWithUnexpectedAudience() throws Exception {
        String token = signedJwt("another-audience", Instant.parse("2099-01-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectJwtWithUnexpectedIssuer() throws Exception {
        String token = signedJwt(
            "https://another-issuer.test",
            "finance-tracker-test",
            Instant.parse("2099-01-01T00:00:00Z")
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {
        String token = signedJwt("finance-tracker-test", Instant.parse("2020-01-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectJwtWithInvalidSignature() throws Exception {
        String signedToken = signedJwt("finance-tracker-test", Instant.parse("2099-01-01T00:00:00Z"));
        int signatureStart = signedToken.lastIndexOf('.') + 1;
        String token = signedToken.substring(0, signatureStart)
                + (signedToken.charAt(signatureStart) == 'a' ? "b" : "a")
                + signedToken.substring(signatureStart + 1);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectPasswordExceedingBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"person@example.test","password":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String signedJwt(String audience, Instant expiresAt) {
        return signedJwt("https://issuer.test", audience, expiresAt);
    }

    private String signedJwt(String issuer, String audience, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("47e9df66-d97c-4e6d-8fd5-8375f898ef9c")
                .audience(List.of(audience))
                .issuedAt(Instant.parse("2019-01-01T00:00:00Z"))
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();
    }
}

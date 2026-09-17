package ru.otus.financetracker.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;


@AutoConfigureMockMvc
class AuthControllerIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("Регистрация пользователя сохраняет пароль в виде bcrypt-хеша")
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
    @DisplayName("Регистрация отклоняет пароль, не соответствующий требованиям")
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
    @DisplayName("Повторная регистрация с тем же email возвращает успешный статус без дубликата")
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
    @DisplayName("Вход выдает JWT и возвращает данные текущего пользователя")
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
    @DisplayName("Защищенный профиль недоступен без JWT")
    void shouldRejectProtectedEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Вход с неверными учетными данными не раскрывает существование пользователя")
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
    @DisplayName("JWT с другой аудиторией отклоняется")
    void shouldRejectJwtWithUnexpectedAudience() throws Exception {
        String token = signedJwt("another-audience", Instant.parse("2099-01-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("JWT с другим издателем отклоняется")
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
    @DisplayName("Просроченный JWT отклоняется")
    void shouldRejectExpiredJwt() throws Exception {
        String token = signedJwt("finance-tracker-test", Instant.parse("2020-01-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("JWT с недействительной подписью отклоняется")
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
    @DisplayName("Вход отклоняет пароль длиннее ограничения bcrypt")
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

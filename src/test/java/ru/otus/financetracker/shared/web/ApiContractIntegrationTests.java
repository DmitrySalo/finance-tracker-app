package ru.otus.financetracker.shared.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.financetracker.shared.PageResponse;
import ru.otus.financetracker.application.identity.UserRegistrationRepository;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "JWT_ISSUER=https://issuer.test",
        "JWT_AUDIENCE=finance-tracker-test",
        "JWT_SECRET=test-signing-secret-with-at-least-32-characters",
        "CORS_ALLOWED_ORIGINS=https://frontend.test",
        "MAX_REQUEST_SIZE=1MB",
        "MAX_CSV_FILE_SIZE=512KB",
        "MAX_CSV_ROWS=100",
        "MAX_REQUEST_HEADER_SIZE=8KB",
        "TEST_DATASOURCE_URL=jdbc:postgresql://localhost:5432/finance_tracker_test",
        "TEST_DATASOURCE_USERNAME=test",
        "TEST_DATASOURCE_PASSWORD=test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiContractIntegrationTests.TestController.class)
class ApiContractIntegrationTests {

    @MockitoBean
    private UserRegistrationRepository userRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnStableValidationErrorWithIncomingCorrelationId() throws Exception {
        mockMvc.perform(post("/api/v1/test/validation")
                        .with(user("test-user"))
                        .header(CorrelationIdFilter.HEADER_NAME, "request-123")
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "request-123"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.traceId").value("request-123"))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].code").value("NOT_BLANK"));
    }

    @Test
    void shouldReturnUnauthorizedErrorForAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/test/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void shouldReturnNotFoundErrorForMissingResource() throws Exception {
        mockMvc.perform(get("/api/v1/test/missing").with(user("test-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundErrorForUnknownApiPath() throws Exception {
        mockMvc.perform(get("/api/v1/test/unknown").with(user("test-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldReturnConflictErrorForOptimisticLockFailure() throws Exception {
        mockMvc.perform(get("/api/v1/test/conflict").with(user("test-user")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Resource state conflict."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldReturnCommonPaginationEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test/paged").with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("transaction"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void shouldReturnValidationErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/test/validation")
                        .with(user("test-user"))
                        .contentType("application/json")
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed."));
    }

    @Test
    void shouldReturnPayloadTooLargeWhenUnreadableMessageWasCausedBySizeLimit() throws Exception {
        mockMvc.perform(get("/api/v1/test/payload-too-large").with(user("test-user")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Request body exceeds the allowed size."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldReturnForbiddenErrorWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test/forbidden").with(user("test-user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldAllowCorsPreflightForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/test/secured")
                        .header("Origin", "https://frontend.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.test"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("GET")));
    }

    @Test
    void shouldExposeHealthEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnFieldViolationForInvalidRequestParameter() throws Exception {
        mockMvc.perform(get("/api/v1/test/paged-validation?page=0").with(user("test-user")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("page"))
                .andExpect(jsonPath("$.violations[0].code").value("MIN"));
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class TestController {

        @PostMapping("/validation")
        ResponseEntity<Void> validate(@Valid @RequestBody TestRequest request) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/secured")
        ResponseEntity<Void> secured() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/missing")
        ResponseEntity<Void> missing() {
            throw new ResourceNotFoundException();
        }

        @GetMapping("/conflict")
        ResponseEntity<Void> conflict() {
            throw new OptimisticLockingFailureException("version conflict");
        }

        @GetMapping("/paged")
        PageResponse<String> paged() {
            return new PageResponse<>(List.of("transaction"), new PageResponse.Page(0, 20, 1, 1));
        }

        @GetMapping("/forbidden")
        ResponseEntity<Void> forbidden() {
            throw new AccessDeniedException("access denied");
        }

        @GetMapping("/paged-validation")
        ResponseEntity<Void> validatePage(@RequestParam @Min(1) int page) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/payload-too-large")
        ResponseEntity<Void> payloadTooLarge() {
            var exception = new HttpMessageNotReadableException("Request is too large", mock(HttpInputMessage.class));
            exception.initCause(new RequestSizeExceededException());
            throw exception;
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}

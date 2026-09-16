package ru.otus.financetracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import ru.otus.financetracker.configuration.ApplicationProperties;
import ru.otus.financetracker.application.identity.UserRegistrationRepository;
import ru.otus.financetracker.application.identity.UserAuthenticationRepository;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.application.transactions.TransactionRepository;
import ru.otus.financetracker.application.audit.AuditLogRepository;
import ru.otus.financetracker.application.budgets.BudgetRepository;
import tools.jackson.databind.ObjectMapper;

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
@ActiveProfiles("test")
class FinanceTrackerApplicationTests {

    @MockitoBean
    private UserRegistrationRepository userRegistrationRepository;

    @MockitoBean
    private UserAuthenticationRepository userAuthenticationRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private BudgetRepository budgetRepository;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Autowired
    FinanceTrackerApplicationTests(Clock clock, ObjectMapper objectMapper, Validator validator) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldConfigureUtcClockAndJsonFormats() throws Exception {
        assertThat(clock.getZone().getId()).isEqualTo("UTC");
        assertThat(objectMapper.writeValueAsString(new JsonPayload(
                LocalDate.of(2026, 9, 16),
                new BigDecimal("1E+8")
        ))).isEqualTo("{\"date\":\"2026-09-16\",\"amount\":\"100000000\"}");
    }

    @Test
    void shouldRejectInconsistentSizeLimits() {
        var limits = new ApplicationProperties.Limits(
                org.springframework.util.unit.DataSize.ofKilobytes(1),
                org.springframework.util.unit.DataSize.ofKilobytes(2),
                100,
                5,
                Duration.ofMinutes(1)
        );

        assertThat(validator.validate(limits))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("consistent");
    }

    private record JsonPayload(LocalDate date, BigDecimal amount) {
    }
}

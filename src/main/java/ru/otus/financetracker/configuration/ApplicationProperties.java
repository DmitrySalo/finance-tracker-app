package ru.otus.financetracker.configuration;

import java.time.ZoneId;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record ApplicationProperties(
        @NotNull ZoneId timeZone,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Limits limits
) {

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotBlank @Size(min = 32) String secret,
            @NotNull Duration accessTokenTtl
    ) {

        @AssertTrue(message = "Access token TTL must be positive.")
        public boolean isAccessTokenTtlPositive() {
            return accessTokenTtl != null && !accessTokenTtl.isNegative() && !accessTokenTtl.isZero();
        }
    }

    public record Cors(
            @NotEmpty List<@NotBlank @Pattern(regexp = "https?://[^/]+") String> allowedOrigins
    ) {
    }

    public record Limits(
            @NotNull DataSize maxRequestSize,
            @NotNull DataSize maxCsvFileSize,
            @Min(1) int maxCsvRows,
            @Min(1) int registrationMaxAttempts,
            @NotNull Duration registrationWindow
    ) {

        @AssertTrue(message = "Request and CSV file size limits must be positive, and the CSV file size limit must not exceed the request size limit.")
        public boolean isConsistent() {
            return maxRequestSize != null
                    && maxCsvFileSize != null
                    && maxRequestSize.toBytes() > 0
                    && maxCsvFileSize.toBytes() > 0
                    && registrationWindow != null
                    && !registrationWindow.isNegative()
                    && !registrationWindow.isZero()
                    && maxCsvFileSize.compareTo(maxRequestSize) <= 0;
        }
    }
}

package ru.otus.financetracker.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12) @Utf8ByteLength(max = 72) String password,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String baseCurrency
) {
}

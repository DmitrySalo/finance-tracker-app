package ru.otus.financetracker.application.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAuthenticationServiceTests {

    private final UserAuthenticationRepository repository = mock(UserAuthenticationRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);
    private final UserAuthenticationService service = new UserAuthenticationService(
            repository,
            passwordEncoder,
            accessTokenIssuer
    );

    @Test
    @DisplayName("Вход с неизвестным email проверяет фиктивный хеш")
    void shouldCheckDummyHashWhenEmailIsUnknown() {
        when(repository.findByEmail("unknown@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login("unknown@example.test", "a-secure-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches("a-secure-password", "$2a$10$7EqJtq98hPqEX7fNZaFWoO5Cb3t0kEwYP0xxvS07Ce/yBfYlwj6Zm");
    }
}

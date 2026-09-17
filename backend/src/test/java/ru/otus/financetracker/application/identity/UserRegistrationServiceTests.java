package ru.otus.financetracker.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.otus.financetracker.domain.identity.User;

class UserRegistrationServiceTests {

    private final UserRegistrationRepository repository = mock(UserRegistrationRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-16T12:00:00Z"),
            ZoneOffset.UTC
    );
    private final UserRegistrationService service = new UserRegistrationService(
            repository,
            passwordEncoder,
            clock
    );

    @Test
    @DisplayName("Регистрация нормализует email, хеширует пароль и сохраняет пользователя")
    void shouldNormalizeEmailHashPasswordAndSaveUser() {
        when(passwordEncoder.encode("a-secure-password")).thenReturn("hashed-password");

        service.register(new RegisterUserCommand(
                " Person@Example.Test ",
                "a-secure-password",
                " Test User ",
                "USD"
        ));

        var userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(userCaptor.capture());
        var user = userCaptor.getValue();
        assertThat(user.email()).isEqualTo("person@example.test");
        assertThat(user.passwordHash()).isEqualTo("hashed-password");
        assertThat(user.displayName()).isEqualTo("Test User");
        assertThat(user.baseCurrency()).isEqualTo("USD");
        assertThat(user.createdAt()).isEqualTo(clock.instant());
        assertThat(user.updatedAt()).isEqualTo(clock.instant());
        assertThat(user.version()).isZero();
    }

    @Test
    @DisplayName("Повторный email не раскрывается при регистрации")
    void shouldNotExposeDuplicateEmailFailure() {
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        doThrow(new DuplicateEmailException(new RuntimeException())).when(repository).save(any());

        assertThatCode(() -> service.register(new RegisterUserCommand(
                "person@example.test", "a-secure-password", "Test User", "USD"
        ))).doesNotThrowAnyException();
    }
}

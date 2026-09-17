package ru.otus.financetracker.application.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.otus.financetracker.domain.identity.User;

@Service
public class UserRegistrationService {

    private final UserRegistrationRepository userRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserRegistrationService(UserRegistrationRepository userRegistrationRepository,
                                   PasswordEncoder passwordEncoder, Clock clock) {
        this.userRegistrationRepository = userRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public void register(RegisterUserCommand command) {
        Instant now = clock.instant();
        var user = new User(
                UUID.randomUUID(),
                command.email().strip().toLowerCase(Locale.ROOT),
                passwordEncoder.encode(command.password()),
                command.displayName().strip(),
                command.baseCurrency(),
                now,
                now,
                0
        );
        try {
            userRegistrationRepository.save(user);
        } catch (DuplicateEmailException exception) {
            // The public response must not disclose whether this email is registered.
        }
    }
}

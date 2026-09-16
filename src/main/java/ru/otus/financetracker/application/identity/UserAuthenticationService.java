package ru.otus.financetracker.application.identity;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.domain.identity.User;

@Service
public class UserAuthenticationService {

    private static final String DUMMY_BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5Cb3t0kEwYP0xxvS07Ce/yBfYlwj6Zm";

    private final UserAuthenticationRepository userAuthenticationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;

    public UserAuthenticationService(UserAuthenticationRepository userAuthenticationRepository,
                                     PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokenIssuer) {
        this.userAuthenticationRepository = userAuthenticationRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Transactional(readOnly = true)
    public IssuedAccessToken login(String email, String password) {
        var user = userAuthenticationRepository.findByEmail(email.strip().toLowerCase(Locale.ROOT));
        String passwordHash = user.map(User::passwordHash).orElse(DUMMY_BCRYPT_HASH);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return accessTokenIssuer.issue(user.orElseThrow());
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(UUID userId) {
        return userAuthenticationRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
    }
}

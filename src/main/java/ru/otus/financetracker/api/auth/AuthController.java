package ru.otus.financetracker.api.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.financetracker.application.identity.RegisterUserCommand;
import ru.otus.financetracker.application.identity.UserRegistrationService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;
    private final RegistrationRateLimiter registrationRateLimiter;

    public AuthController(UserRegistrationService userRegistrationService, RegistrationRateLimiter registrationRateLimiter) {
        this.userRegistrationService = userRegistrationService;
        this.registrationRateLimiter = registrationRateLimiter;
    }

    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterUserRequest request, HttpServletRequest servletRequest) {
        registrationRateLimiter.check(servletRequest.getRemoteAddr(), request.email().strip().toLowerCase(Locale.ROOT));
        userRegistrationService.register(new RegisterUserCommand(
                request.email(), request.password(), request.displayName(), request.baseCurrency()
        ));
        return ResponseEntity.status(201).build();
    }
}

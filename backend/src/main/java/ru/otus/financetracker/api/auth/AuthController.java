package ru.otus.financetracker.api.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.financetracker.application.identity.RegisterUserCommand;
import ru.otus.financetracker.application.identity.UserAuthenticationService;
import ru.otus.financetracker.application.identity.UserRegistrationService;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, sign-in, and current user operations")
public class AuthController {

    private final UserRegistrationService userRegistrationService;
    private final AuthenticationRateLimiter authenticationRateLimiter;
    private final UserAuthenticationService userAuthenticationService;

    public AuthController(UserRegistrationService userRegistrationService, AuthenticationRateLimiter authenticationRateLimiter,
                          UserAuthenticationService userAuthenticationService) {
        this.userRegistrationService = userRegistrationService;
        this.authenticationRateLimiter = authenticationRateLimiter;
        this.userAuthenticationService = userAuthenticationService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a user", description = "Creates a user account.")
    @SecurityRequirements
    @ApiResponses({@ApiResponse(responseCode = "201", description = "User registered"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "429", description = "Registration rate limit exceeded")})
    ResponseEntity<Void> register(@Valid @RequestBody RegisterUserRequest request, HttpServletRequest servletRequest) {
        authenticationRateLimiter.check(servletRequest.getRemoteAddr(), request.email().strip().toLowerCase(Locale.ROOT));
        userRegistrationService.register(new RegisterUserCommand(
                request.email(), request.password(), request.displayName(), request.baseCurrency()
        ));
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Returns a short-lived Bearer access token.")
    @SecurityRequirements
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "429", description = "Login rate limit exceeded")})
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        authenticationRateLimiter.check(servletRequest.getRemoteAddr(), request.email().strip().toLowerCase(Locale.ROOT));
        var token = userAuthenticationService.login(request.email(), request.password());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt());
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Current user"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")})
    CurrentUserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        var user = userAuthenticationService.getCurrentUser(java.util.UUID.fromString(jwt.getSubject()));
        return new CurrentUserResponse(user.id(), user.email(), user.displayName(), user.baseCurrency());
    }
}

package ru.otus.financetracker.configuration;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import ru.otus.financetracker.shared.ErrorCode;
import ru.otus.financetracker.shared.web.ApiErrorResponseWriter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    JwtEncoder jwtEncoder(ApplicationProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(ApplicationProperties properties) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.jwt().audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()), audienceValidator
        ));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorResponseWriter errorResponseWriter,
                                            CorsConfigurationSource corsConfigurationSource, Environment environment) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health").permitAll()
                            .requestMatchers("/api/v1/auth/register").permitAll()
                            .requestMatchers("/api/v1/auth/login").permitAll();
                    if (environment.matchesProfiles("dev")) {
                        authorize.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, authenticationException) -> errorResponseWriter.write(
                                response, request, 401, ErrorCode.UNAUTHORIZED, "Authentication is required."
                        ))
                        .jwt(jwt -> { }))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, authenticationException) -> errorResponseWriter.write(response, request,
                                401, ErrorCode.UNAUTHORIZED, "Authentication is required.")
                ).accessDeniedHandler((request, response, accessDeniedException) ->
                        errorResponseWriter.write(response, request, 403, ErrorCode.FORBIDDEN, "Access is denied.")));
        return http.build();
    }

    private SecretKey jwtSecretKey(ApplicationProperties properties) {
        return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}

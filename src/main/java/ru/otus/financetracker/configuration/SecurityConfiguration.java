package ru.otus.financetracker.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import ru.otus.financetracker.shared.ErrorCode;
import ru.otus.financetracker.shared.web.ApiErrorResponseWriter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorResponseWriter errorResponseWriter,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, authenticationException) -> errorResponseWriter.write(response, request,
                                401, ErrorCode.UNAUTHORIZED, "Authentication is required.")
                ).accessDeniedHandler((request, response, accessDeniedException) ->
                        errorResponseWriter.write(response, request, 403, ErrorCode.FORBIDDEN, "Access is denied.")));
        return http.build();
    }
}

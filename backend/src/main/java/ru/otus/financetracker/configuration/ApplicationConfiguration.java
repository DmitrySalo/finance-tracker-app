package ru.otus.financetracker.configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.otus.financetracker.shared.web.ApiErrorResponseWriter;
import ru.otus.financetracker.shared.web.CorrelationIdFilter;
import ru.otus.financetracker.shared.web.RequestSizeLimitFilter;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    Clock clock(ApplicationProperties properties) {
        return Clock.system(properties.timeZone());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JacksonModule financeJacksonModule() {
        return new SimpleModule().addSerializer(BigDecimal.class, new BigDecimalSerializer());
    }

    @Bean
    RequestSizeLimitFilter requestSizeLimitFilter(
            ApplicationProperties properties,
            ApiErrorResponseWriter errorResponseWriter
    ) {
        return new RequestSizeLimitFilter(properties, errorResponseWriter);
    }

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            ApplicationProperties properties
    ) {
        var corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(properties.cors().allowedOrigins());
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration);
        return source;
    }

    private static final class BigDecimalSerializer extends StdSerializer<BigDecimal> {

        private BigDecimalSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(
                BigDecimal value,
                tools.jackson.core.JsonGenerator generator,
                tools.jackson.databind.SerializationContext context
        ) throws tools.jackson.core.JacksonException {
            generator.writeString(value.toPlainString());
        }
    }
}

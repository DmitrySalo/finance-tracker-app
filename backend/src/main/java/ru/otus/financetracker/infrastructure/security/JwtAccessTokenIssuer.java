package ru.otus.financetracker.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import ru.otus.financetracker.application.identity.AccessTokenIssuer;
import ru.otus.financetracker.application.identity.IssuedAccessToken;
import ru.otus.financetracker.configuration.ApplicationProperties;
import ru.otus.financetracker.domain.identity.User;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final ApplicationProperties properties;
    private final Clock clock;

    public JwtAccessTokenIssuer(JwtEncoder jwtEncoder, ApplicationProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.jwt().accessTokenTtl());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.id().toString())
                .audience(List.of(properties.jwt().audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                ))
                .getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }
}

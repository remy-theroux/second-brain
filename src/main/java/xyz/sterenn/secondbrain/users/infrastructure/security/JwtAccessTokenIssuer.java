package xyz.sterenn.secondbrain.users.infrastructure.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;
import xyz.sterenn.secondbrain.users.domain.valueobject.AccessToken;

// JwtConfiguration's encoder is built on a symmetric key: it sets HS256 itself, there is no
// header to build here.
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;

    JwtAccessTokenIssuer(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    public AccessToken issue(UUID subject, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return new AccessToken(value, expiresAt);
    }
}

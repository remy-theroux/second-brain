package xyz.sterenn.secondbrain.users.infrastructure.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;
import xyz.sterenn.secondbrain.users.domain.valueobject.AccessToken;

// L'encodeur de JwtConfiguration est bâti sur une clé symétrique : il pose HS256 lui-même,
// il n'y a pas d'en-tête à construire ici.
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;

    JwtAccessTokenIssuer(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    public AccessToken issue(UUID subject, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .subject(subject.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        String valeur =
                jwtEncoder.encode(JwtEncoderParameters.from(revendications)).getTokenValue();

        return new AccessToken(valeur, expiresAt);
    }
}

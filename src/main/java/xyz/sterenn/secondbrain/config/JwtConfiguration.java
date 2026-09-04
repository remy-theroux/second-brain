package xyz.sterenn.secondbrain.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Le secret n'a aucune valeur par défaut : sans lui, l'application ne démarre pas. */
@Configuration
public class JwtConfiguration {

    /** HS256 exige une clé de 256 bits. */
    public static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey secretKey;

    public JwtConfiguration(@Value("${secondbrain.jwt.secret}") String secret) {
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("secondbrain.jwt.secret doit faire au moins " + MIN_SECRET_LENGTH
                    + " octets pour signer en HS256 ; " + octets.length + " reçus");
        }
        this.secretKey = new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(secretKey).build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}

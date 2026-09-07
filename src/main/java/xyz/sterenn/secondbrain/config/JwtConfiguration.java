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

/** The secret has no default value: without it, the application does not start. */
@Configuration
public class JwtConfiguration {

    /** HS256 requires a 256-bit key. */
    public static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey secretKey;

    public JwtConfiguration(@Value("${secondbrain.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("secondbrain.jwt.secret doit faire au moins " + MIN_SECRET_LENGTH
                    + " octets pour signer en HS256 ; " + bytes.length + " reçus");
        }
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
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

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

/**
 * Signature et vérification des jetons d'accès, en HS256 sur un secret partagé.
 *
 * <p>Un seul processus signe et vérifie : une paire de clés asymétrique n'apporterait
 * rien tant qu'aucun tiers ne valide de jeton. Les deux beans sont ici, et non dans
 * {@code users/infrastructure/}, parce que le décodeur sert le filtre de sécurité de
 * toute l'application alors que l'encodeur sert le contexte {@code users} : le secret
 * qu'ils partagent, lui, est transverse.
 *
 * <p>Le secret n'a pas de valeur par défaut : sans {@code SECONDBRAIN_JWT_SECRET},
 * l'application ne démarre pas. Un secret de signature par défaut serait un secret public.
 */
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

    /**
     * {@code withSecretKey} pose déjà l'en-tête HS256 par défaut : l'appelant encode
     * sans avoir à construire de {@code JwsHeader}.
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(secretKey).build();
    }

    /**
     * Le décodeur applique par défaut la validation des horodatages, avec une tolérance
     * d'horloge de 60 secondes.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}

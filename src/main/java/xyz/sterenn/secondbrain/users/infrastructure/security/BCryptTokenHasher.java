package xyz.sterenn.secondbrain.users.infrastructure.security;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;

/**
 * Adapter du port {@link TokenHasher}, adossé à l'encodeur délégant de Spring Security :
 * les empreintes sont préfixées de l'algorithme ({@code {bcrypt}...}), ce qui permettra
 * d'en changer sans invalider les jetons en vol.
 *
 * <p>Le salt est tiré par BCrypt à chaque hachage et embarqué dans l'empreinte : deux
 * hachages du même jeton diffèrent, et la comparaison passe forcément par
 * {@link #matches}. La troncature de BCrypt au 72e octet est sans effet ici — un jeton
 * fait 43 caractères.
 */
@Component
public class BCryptTokenHasher implements TokenHasher {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String hash(String rawToken) {
        return encoder.encode(rawToken);
    }

    @Override
    public boolean matches(String rawToken, String hash) {
        return encoder.matches(rawToken, hash);
    }
}

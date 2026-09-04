package xyz.sterenn.secondbrain.users.infrastructure.security;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;

// Le salt est tiré à chaque hachage : deux empreintes du même jeton diffèrent, et la
// comparaison passe forcément par matches.
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

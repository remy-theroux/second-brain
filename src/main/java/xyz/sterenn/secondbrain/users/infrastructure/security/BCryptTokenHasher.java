package xyz.sterenn.secondbrain.users.infrastructure.security;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;

// The salt is drawn on every hash: two digests of the same token differ, so comparison
// necessarily goes through matches.
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

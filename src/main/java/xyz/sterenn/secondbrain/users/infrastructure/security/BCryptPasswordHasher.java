package xyz.sterenn.secondbrain.users.infrastructure.security;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;

// BCrypt ignore les octets au-delà du 72e : voir ADR-0005.
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}

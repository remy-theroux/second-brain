package xyz.sterenn.secondbrain.users.infrastructure.security;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;

/**
 * Adapter du port {@link PasswordHasher}, adossé à l'encodeur délégant de Spring
 * Security : les empreintes sont préfixées de l'algorithme ({@code {bcrypt}...}), ce
 * qui permettra d'en changer sans invalider les mots de passe existants.
 *
 * <p>BCrypt ignore les octets au-delà du 72e : deux mots de passe très longs partageant
 * leurs 72 premiers octets sont équivalents. Comportement standard, acceptable au regard
 * du minimum de 12 caractères imposé par la politique.
 */
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

package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Adapter du port {@link UserRepository}. Son autre rôle est de traduire les erreurs
 * techniques en erreurs métier, pour qu'aucune exception Spring ne remonte à
 * l'application ni au domaine.
 */
@Component
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepositoryAdapter(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        try {
            // saveAndFlush : sans flush explicite, la violation d'unicité ne surviendrait
            // qu'au commit, hors de portée du try/catch.
            return jpa.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(user.getEmail());
        }
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email);
    }
}

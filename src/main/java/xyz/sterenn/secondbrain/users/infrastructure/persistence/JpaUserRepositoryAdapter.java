package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
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
 *
 * <p>{@link #save(User)} sert deux appelants aux intentions différentes : l'inscription,
 * qui insère un nouveau compte et peut violer l'unicité de l'email, et
 * {@code VerifyAccountHandler}, qui ne fait que basculer {@code verified} sur un compte
 * déjà persisté et ne peut pas déclencher cette violation-là. La traduction en
 * {@link EmailAlreadyUsedException} ne s'applique donc qu'à l'insertion : {@code save} sur
 * un compte déjà pourvu d'un identifiant laisse remonter l'exception technique telle
 * quelle.
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
        // Un identifiant déjà présent signale une mise à jour (le compte existe déjà en
        // base) : seule l'insertion peut violer l'unicité de l'email.
        boolean insertion = user.getId() == null;
        try {
            // saveAndFlush : sans flush explicite, la violation d'unicité ne surviendrait
            // qu'au commit, hors de portée du try/catch.
            return jpa.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (!insertion) {
                throw e;
            }
            throw new EmailAlreadyUsedException(user.getEmail());
        }
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }
}

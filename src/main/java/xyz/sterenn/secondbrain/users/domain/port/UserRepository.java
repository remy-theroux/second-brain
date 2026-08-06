package xyz.sterenn.secondbrain.users.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Port sortant vers le stockage des comptes. Le domaine énonce ce dont il a besoin ;
 * l'implémentation vit dans {@code users.infrastructure.persistence}.
 */
public interface UserRepository {

    boolean existsByEmail(Email email);

    /**
     * @throws EmailAlreadyUsedException si la contrainte d'unicité est violée à
     *         l'écriture — l'adapter traduit l'erreur technique en erreur métier
     */
    User save(User user);

    Optional<User> findByEmail(Email email);

    Optional<User> findById(UUID id);
}

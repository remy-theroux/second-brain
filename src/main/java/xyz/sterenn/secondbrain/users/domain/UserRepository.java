package xyz.sterenn.secondbrain.users.domain;

import java.util.Optional;

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
}

package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit
 * en dépendre. Les requêtes dérivées acceptent un {@link Email} — le paramètre traverse
 * l'{@link EmailAttributeConverter} comme la colonne, sans {@code @Convert} explicite
 * puisque le converter est {@code autoApply}.
 */
interface SpringDataUserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(Email email);

    Optional<User> findByEmail(Email email);
}

package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.User;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit
 * en dépendre. Les requêtes dérivées acceptent un {@link Email} — le paramètre traverse
 * l'{@code EmailAttributeConverter} comme la colonne.
 */
interface SpringDataUserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(Email email);

    Optional<User> findByEmail(Email email);
}

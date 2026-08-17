package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit
 * en dépendre.
 */
interface SpringDataVerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByUserId(UUID userId);
}

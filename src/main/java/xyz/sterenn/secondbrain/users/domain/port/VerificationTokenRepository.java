package xyz.sterenn.secondbrain.users.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;

/**
 * Port sortant vers le stockage des jetons de vérification. Un compte n'a qu'un jeton à
 * la fois tant que le renvoi de lien n'existe pas.
 */
public interface VerificationTokenRepository {

    VerificationToken save(VerificationToken token);

    Optional<VerificationToken> findByUserId(UUID userId);
}

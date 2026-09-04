package xyz.sterenn.secondbrain.users.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;

/** Stocke et relit le jeton de vérification d'un compte, qui n'en a qu'un à la fois. */
public interface VerificationTokenRepository {

    VerificationToken save(VerificationToken token);

    Optional<VerificationToken> findByUserId(UUID userId);
}

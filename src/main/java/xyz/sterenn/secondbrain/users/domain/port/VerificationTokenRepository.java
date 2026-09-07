package xyz.sterenn.secondbrain.users.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;

/** Stores and reads back the verification token of an account, which holds only one at a time. */
public interface VerificationTokenRepository {

    VerificationToken save(VerificationToken token);

    Optional<VerificationToken> findByUserId(UUID userId);
}

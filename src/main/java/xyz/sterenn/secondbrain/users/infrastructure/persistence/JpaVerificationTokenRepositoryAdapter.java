package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;

/**
 * Adapter du port {@link VerificationTokenRepository}.
 *
 * <p>Contrairement à {@link JpaUserRepositoryAdapter}, aucune erreur technique n'est ici
 * traduite en erreur métier : la seule contrainte susceptible d'être violée est
 * {@code uq_users_verification_tokens_user}, et rien dans le domaine actuel ne peut
 * émettre un second jeton pour un même compte.
 */
@Component
public class JpaVerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final SpringDataVerificationTokenRepository springDataVerificationTokenRepository;

    JpaVerificationTokenRepositoryAdapter(
            SpringDataVerificationTokenRepository springDataVerificationTokenRepository) {
        this.springDataVerificationTokenRepository = springDataVerificationTokenRepository;
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        // saveAndFlush : le jeton référence l'utilisateur par une clé étrangère, la
        // violation éventuelle doit survenir ici et non au commit.
        return springDataVerificationTokenRepository.saveAndFlush(token);
    }

    @Override
    public Optional<VerificationToken> findByUserId(UUID userId) {
        return springDataVerificationTokenRepository.findByUserId(userId);
    }
}

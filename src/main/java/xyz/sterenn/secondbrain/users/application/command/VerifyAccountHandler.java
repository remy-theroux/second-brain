package xyz.sterenn.secondbrain.users.application.command;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;

/**
 * Orchestre la vérification : interprétation du lien, comparaison du jeton, consommation.
 *
 * <p>Les règles « expiré » et « déjà utilisé » appartiennent à {@code VerificationToken} :
 * ce handler ne fait que les laisser remonter. Aucun {@code @Transactional} ici — la
 * transaction appartient au bus, et la moindre exception annule tout.
 */
@Component
public class VerifyAccountHandler implements CommandHandler<VerifyAccount> {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    public VerifyAccountHandler(
            UserRepository userRepository,
            VerificationTokenRepository verificationTokenRepository,
            TokenHasher tokenHasher,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    @Override
    public void handle(VerifyAccount command) {
        UUID accountId = parseAccountId(command.accountId());

        VerificationToken token = verificationTokenRepository.findByUserId(accountId)
            .orElseThrow(InvalidVerificationLinkException::new);

        // Le hash est salé : la seule comparaison possible passe par le hasher.
        if (!tokenHasher.matches(command.rawToken(), token.getTokenHash())) {
            throw new InvalidVerificationLinkException();
        }

        // Lève « déjà utilisé » ou « expiré » le cas échéant.
        token.consume(clock.instant());
        verificationTokenRepository.save(token);

        User user = userRepository.findById(accountId).orElseThrow(InvalidVerificationLinkException::new);
        user.verify();
        userRepository.save(user);
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Un identifiant illisible se refuse comme un lien invalide, pas comme une panne.
            throw new InvalidVerificationLinkException();
        }
    }
}

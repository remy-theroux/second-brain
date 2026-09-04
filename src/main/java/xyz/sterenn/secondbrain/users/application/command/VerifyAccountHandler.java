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
            Clock clock) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    @Override
    public void handle(VerifyAccount command) {
        UUID accountId = parseAccountId(command.accountId());

        VerificationToken token =
                verificationTokenRepository.findByUserId(accountId).orElseThrow(InvalidVerificationLinkException::new);

        if (!tokenHasher.matches(command.rawToken(), token.getTokenHash())) {
            throw new InvalidVerificationLinkException();
        }

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
            throw new InvalidVerificationLinkException();
        }
    }
}

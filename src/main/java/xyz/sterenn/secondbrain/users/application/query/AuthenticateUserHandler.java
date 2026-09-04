package xyz.sterenn.secondbrain.users.application.query;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;
import xyz.sterenn.secondbrain.users.domain.AccessTokenPolicy;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidCredentialsException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.exception.UnverifiedAccountException;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.AccessToken;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Component
public class AuthenticateUserHandler implements QueryHandler<AuthenticateUser, AccessTokenView> {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;

    public AuthenticateUserHandler(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokenIssuer,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.clock = clock;
    }

    @Override
    public AccessTokenView handle(AuthenticateUser query) {
        User user = userRepository.findByEmail(parseEmail(query.email())).orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(query.rawPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Mot de passe avant vérification d'adresse : ne pas inverser, le refus « compte non
        // vérifié » ne doit s'obtenir qu'après avoir donné le bon mot de passe.
        if (!user.isVerified()) {
            throw new UnverifiedAccountException();
        }

        Instant maintenant = clock.instant();
        AccessToken accessToken =
                accessTokenIssuer.issue(user.getId(), maintenant, AccessTokenPolicy.expiresAt(maintenant));

        return new AccessTokenView(accessToken.value(), accessToken.expiresIn(maintenant));
    }

    private Email parseEmail(String email) {
        try {
            return new Email(email);
        } catch (InvalidEmailException e) {
            throw new InvalidCredentialsException();
        }
    }
}

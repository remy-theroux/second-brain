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

/**
 * Orchestre la connexion : normalisation de l'email, comparaison du mot de passe, contrôle
 * de vérification, émission du jeton.
 *
 * <p>Aucun {@code @Transactional} ici : {@code SpringQueryBus.ask} ouvre déjà une
 * transaction en lecture seule, et cette query n'écrit rien.
 *
 * <p>L'ordre des contrôles est un choix de sécurité, pas une commodité : le refus « compte
 * non vérifié » ne peut être obtenu qu'après avoir donné le bon mot de passe.
 */
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
            // Une saisie mal formée ne correspond à aucun compte : même refus, même message.
            throw new InvalidCredentialsException();
        }
    }
}

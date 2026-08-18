package xyz.sterenn.secondbrain.users.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.PasswordPolicy;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Orchestre l'inscription : conversion en value objects, contrôles métier, écriture, puis
 * émission du jeton de vérification et notification.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch} et couvre tout ce qui suit, envoi compris. Une panne
 * du canal de notification annule donc l'inscription : tant qu'il n'existe pas de renvoi
 * de lien, un compte créé sans notification serait définitivement invérifiable.
 */
@Component
public class RegisterUserHandler implements CommandHandler<RegisterUser> {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final VerificationTokenRepository verificationTokenRepository;
    private final TokenHasher tokenHasher;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public RegisterUserHandler(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            VerificationTokenRepository verificationTokenRepository,
            TokenHasher tokenHasher,
            NotificationSender notificationSender,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.verificationTokenRepository = verificationTokenRepository;
        this.tokenHasher = tokenHasher;
        this.notificationSender = notificationSender;
        this.clock = clock;
    }

    @Override
    public void handle(RegisterUser command) {
        // Le constructeur d'Email normalise et lève InvalidEmailException si besoin.
        Email email = new Email(command.email());

        // Contrôles locaux d'abord, aller-retour base ensuite.
        if (!PasswordPolicy.isAcceptable(command.rawPassword())) {
            throw new WeakPasswordException();
        }
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        User user = userRepository.save(User.register(email, passwordHasher.hash(command.rawPassword())));

        // Le clair ne quitte jamais cette méthode autrement que dans la notification :
        // ce qui est persisté, c'est uniquement son empreinte salée.
        RawVerificationToken rawToken = RawVerificationToken.generate();
        verificationTokenRepository.save(
                VerificationToken.issue(user.getId(), tokenHasher.hash(rawToken.value()), clock.instant()));

        notificationSender.send(new VerificationNotification(email, user.getId(), rawToken));
    }
}

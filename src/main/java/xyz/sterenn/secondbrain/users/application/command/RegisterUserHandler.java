package xyz.sterenn.secondbrain.users.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.PasswordPolicy;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;
import xyz.sterenn.secondbrain.users.domain.WeakPasswordException;

/**
 * Orchestre l'inscription : conversion en value objects, contrôles métier, écriture.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch} et couvre tout ce qui suit.
 */
@Component
public class RegisterUserHandler implements CommandHandler<RegisterUser> {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public RegisterUserHandler(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void handle(RegisterUser command) {
        // Le constructeur d'Email normalise et lève InvalidEmailException si besoin.
        Email email = new Email(command.email());

        // Contrôles locaux d'abord, aller-retour base ensuite.
        if (!PasswordPolicy.isAcceptable(command.rawPassword())) {
            throw new WeakPasswordException();
        }
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        users.save(User.register(email, passwordHasher.hash(command.rawPassword())));
    }
}

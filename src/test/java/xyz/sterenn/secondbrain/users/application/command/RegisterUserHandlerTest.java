package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class RegisterUserHandlerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private RecordingNotificationSender notifications;

    @BeforeEach
    void vide_les_notifications() {
        notifications.clear();
    }

    @Test
    void cree_un_compte_non_verifie_avec_un_mot_de_passe_hache() {
        commandBus.dispatch(new RegisterUser("alice@example.com", MOT_DE_PASSE_VALIDE));

        User created =
                userRepository.findByEmail(new Email("alice@example.com")).orElseThrow();
        assertThat(created.getId()).isNotNull();
        assertThat(created.isVerified()).isFalse();
        assertThat(created.getPasswordHash()).isNotEqualTo(MOT_DE_PASSE_VALIDE);
        assertThat(passwordHasher.matches(MOT_DE_PASSE_VALIDE, created.getPasswordHash()))
                .isTrue();
    }

    @Test
    void normalise_l_email_avant_de_le_stocker() {
        commandBus.dispatch(new RegisterUser("  Bob@Example.COM  ", MOT_DE_PASSE_VALIDE));

        assertThat(userRepository.existsByEmail(new Email("bob@example.com"))).isTrue();
    }

    @Test
    void refuse_un_email_deja_utilise() {
        commandBus.dispatch(new RegisterUser("carol@example.com", MOT_DE_PASSE_VALIDE));

        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("CAROL@Example.com", MOT_DE_PASSE_VALIDE)))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .hasMessageContaining("carol@example.com");
    }

    @Test
    void refuse_un_mot_de_passe_trop_faible_sans_creer_de_compte() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("dave@example.com", "court")))
                .isInstanceOf(WeakPasswordException.class);

        assertThat(userRepository.existsByEmail(new Email("dave@example.com"))).isFalse();
    }

    @Test
    void refuse_un_email_mal_forme() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("pas-un-email", MOT_DE_PASSE_VALIDE)))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void valide_le_mot_de_passe_avant_l_unicite_de_l_email() {
        commandBus.dispatch(new RegisterUser("erin@example.com", MOT_DE_PASSE_VALIDE));

        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("erin@example.com", "court")))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void notifie_le_nouveau_compte_de_sa_verification() {
        commandBus.dispatch(new RegisterUser("frank@example.com", MOT_DE_PASSE_VALIDE));

        VerificationNotification notification = notifications.derniere();
        assertThat(notification.recipient()).isEqualTo(new Email("frank@example.com"));
        assertThat(notification.rawToken().value()).isNotBlank();
    }

    @Test
    void emet_un_jeton_dont_seule_l_empreinte_est_stockee() {
        commandBus.dispatch(new RegisterUser("grace@example.com", MOT_DE_PASSE_VALIDE));

        VerificationNotification notification = notifications.derniere();
        VerificationToken jeton = verificationTokenRepository
                .findByUserId(notification.accountId())
                .orElseThrow();

        assertThat(jeton.getTokenHash()).doesNotContain(notification.rawToken().value());
        assertThat(tokenHasher.matches(notification.rawToken().value(), jeton.getTokenHash()))
                .isTrue();
        assertThat(jeton.isConsumed()).isFalse();
    }

    @Test
    void adresse_la_notification_au_compte_reellement_cree() {
        commandBus.dispatch(new RegisterUser("heidi@example.com", MOT_DE_PASSE_VALIDE));

        VerificationNotification notification = notifications.derniere();
        assertThat(userRepository
                        .findByEmail(new Email("heidi@example.com"))
                        .orElseThrow()
                        .getId())
                .isEqualTo(notification.accountId());
    }

    @Test
    void ne_notifie_pas_quand_l_inscription_est_refusee() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("ivan@example.com", "court")))
                .isInstanceOf(WeakPasswordException.class);

        assertThat(notifications.verifications()).isEmpty();
    }
}

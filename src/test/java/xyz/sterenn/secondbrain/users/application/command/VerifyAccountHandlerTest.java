package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Les commandes sont dispatchées par le bus, chemin réel de production. Le jeton en clair
 * est relu dans l'enregistreur de notifications, comme l'utilisateur le lirait dans sa
 * boîte mail.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class VerifyAccountHandlerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingNotificationSender notifications;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void vide_les_notifications() {
        notifications.clear();
    }

    private VerificationNotification inscrit(String email) {
        commandBus.dispatch(new RegisterUser(email, MOT_DE_PASSE_VALIDE));
        return notifications.derniere();
    }

    private void vieillitLeJeton(UUID compte, Duration age) {
        // Le jeton est écrit par le handler avec l'horloge réelle : le faire vieillir en
        // base est le seul moyen d'observer l'expiration sans figer l'horloge du contexte.
        entityManager.flush();
        jdbcTemplate.update(
            "UPDATE users_verification_tokens "
                + "SET expires_at = expires_at - CAST(? AS interval) WHERE user_id = ?",
            age.toHours() + " hours", compte);
        // Sans ce clear, le cache de premier niveau d'Hibernate resservirait l'entité
        // telle qu'elle était avant l'UPDATE, et l'expiration passerait inaperçue.
        entityManager.clear();
    }

    @Test
    void verifie_le_compte_quand_le_jeton_correspond() {
        VerificationNotification notification = inscrit("alice@example.com");

        commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value()));

        assertThat(userRepository.findById(notification.accountId()).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void refuse_un_jeton_qui_ne_correspond_pas() {
        VerificationNotification notification = inscrit("bob@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
            new VerifyAccount(notification.accountId().toString(), "un-autre-jeton")))
            .isInstanceOf(InvalidVerificationLinkException.class);

        assertThat(userRepository.findById(notification.accountId()).orElseThrow().isVerified()).isFalse();
    }

    @Test
    void refuse_un_compte_inconnu() {
        VerificationNotification notification = inscrit("carol@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
            new VerifyAccount(UUID.randomUUID().toString(), notification.rawToken().value())))
            .isInstanceOf(InvalidVerificationLinkException.class);
    }

    @Test
    void refuse_un_identifiant_de_compte_mal_forme() {
        VerificationNotification notification = inscrit("dave@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
            new VerifyAccount("pas-un-uuid", notification.rawToken().value())))
            .isInstanceOf(InvalidVerificationLinkException.class);
    }

    @Test
    void refuse_un_jeton_expire() {
        VerificationNotification notification = inscrit("erin@example.com");
        vieillitLeJeton(notification.accountId(), Duration.ofHours(25));

        assertThatThrownBy(() -> commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value())))
            .isInstanceOf(ExpiredVerificationLinkException.class);

        assertThat(userRepository.findById(notification.accountId()).orElseThrow().isVerified()).isFalse();
    }

    @Test
    void refuse_un_jeton_deja_utilise() {
        VerificationNotification notification = inscrit("frank@example.com");
        commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value()));

        assertThatThrownBy(() -> commandBus.dispatch(new VerifyAccount(
            notification.accountId().toString(), notification.rawToken().value())))
            .isInstanceOf(AlreadyUsedVerificationLinkException.class);

        // Le compte reste vérifié : le second clic ne défait rien.
        assertThat(userRepository.findById(notification.accountId()).orElseThrow().isVerified()).isTrue();
    }
}

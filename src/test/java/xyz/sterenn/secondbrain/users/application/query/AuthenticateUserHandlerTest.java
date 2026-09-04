package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidCredentialsException;
import xyz.sterenn.secondbrain.users.domain.exception.UnverifiedAccountException;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class AuthenticateUserHandlerTest {

    private static final String MOT_DE_PASSE = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void vide_les_notifications_enregistrees() {
        recordingNotificationSender.clear();
    }

    @Test
    void delivre_un_jeton_au_compte_verifie_qui_donne_le_bon_mot_de_passe() {
        UUID compte = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);

        AccessTokenView vue = queryBus.ask(new AuthenticateUser("alice@exemple.fr", MOT_DE_PASSE));

        assertThat(vue.expiresIn()).isEqualTo(3600L);
        assertThat(jwtDecoder.decode(vue.value()).getSubject()).isEqualTo(compte.toString());
    }

    @Test
    void accepte_un_email_saisi_avec_une_casse_differente() {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);

        AccessTokenView vue = queryBus.ask(new AuthenticateUser("ALICE@Exemple.FR", MOT_DE_PASSE));

        assertThat(vue.value()).isNotBlank();
    }

    @Test
    void refuse_un_mot_de_passe_incorrect() {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);

        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("alice@exemple.fr", "chevalpile43")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refuse_un_email_inconnu() {
        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("inconnu@exemple.fr", MOT_DE_PASSE)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refuse_un_email_mal_forme_comme_un_identifiant_faux() {
        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("pas-un-email", MOT_DE_PASSE)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refuse_un_compte_dont_l_adresse_n_est_pas_verifiee() {
        AccountFixture.register(commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);

        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("bob@exemple.fr", MOT_DE_PASSE)))
                .isInstanceOf(UnverifiedAccountException.class)
                .hasMessageContaining("vérifié");
    }

    @Test
    void ne_revele_pas_qu_un_compte_existe_a_qui_ignore_le_mot_de_passe() {
        AccountFixture.register(commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);

        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("bob@exemple.fr", "chevalpile43")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}

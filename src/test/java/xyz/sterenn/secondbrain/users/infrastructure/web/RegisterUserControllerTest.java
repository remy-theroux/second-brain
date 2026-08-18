package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.application.query.FindUserByEmail;
import xyz.sterenn.secondbrain.users.application.query.UserView;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;

/**
 * Couvre les scénarios d'écriture du parcours d'inscription au niveau HTTP, désormais en
 * JSON. CSRF est désactivé côté application : aucun jeton à fournir.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegisterUserControllerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryBus queryBus;

    private static String corps(String email, String motDePasse) {
        return """
            {"email": "%s", "password": "%s"}
            """.formatted(email, motDePasse);
    }

    @Test
    void cree_le_compte_et_repond_201_en_cas_de_succes() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("alice@example.com", MOT_DE_PASSE_VALIDE)))
            .andExpect(status().isCreated());

        Optional<UserView> vue = queryBus.ask(new FindUserByEmail("alice@example.com"));
        assertThat(vue).isPresent();
        assertThat(vue.get().verified()).isFalse();
    }

    @Test
    void refuse_un_email_deja_utilise_avec_une_erreur_sur_le_champ_email() throws Exception {
        mockMvc.perform(post("/api/registrations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corps("bob@example.com", MOT_DE_PASSE_VALIDE)));

        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("bob@example.com", MOT_DE_PASSE_VALIDE)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").doesNotExist());
    }

    @Test
    void refuse_un_mot_de_passe_faible_avec_une_erreur_sur_le_champ_password() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("carol@example.com", "court")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.password").exists())
            .andExpect(jsonPath("$.errors.email").doesNotExist());

        assertThat(queryBus.ask(new FindUserByEmail("carol@example.com"))).isEmpty();
    }

    @Test
    void refuse_un_email_mal_forme_avec_une_erreur_sur_le_champ_email() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("pas-un-email", MOT_DE_PASSE_VALIDE)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void refuse_les_champs_vides_en_nommant_les_deux() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps("", "")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    /**
     * Isolée dans un contexte Spring distinct : le canal de notification y échoue
     * systématiquement, ce qui entrerait en conflit avec le
     * {@link RecordingNotificationSenderConfiguration} de la classe englobante si les deux
     * définissaient chacune un {@code NotificationSender} {@code @Primary} dans le même
     * contexte. {@code @NestedTestConfiguration(OVERRIDE)} fait qu'aucune configuration de
     * {@link RegisterUserControllerTest} n'est héritée ici.
     *
     * <p>Volontairement <strong>sans</strong> {@code @Transactional} : le rollback observé
     * ici est déclenché par {@code SpringCommandBus.dispatch}, une transaction imbriquée
     * dans celle du test. Tant que la transaction englobante du test n'a pas elle-même
     * terminé, ce rollback interne n'est que marqué, pas exécuté : une requête dans le même
     * test verrait encore la ligne. Le nettoyage est donc explicite (voir
     * {@code CommandBusTransactionTest}).
     */
    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @Import({TestcontainersConfiguration.class, QuandLenvoiEchoue.EchecEnvoiConfiguration.class})
    @SpringBootTest
    @AutoConfigureMockMvc
    class QuandLenvoiEchoue {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private QueryBus queryBus;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        /**
         * Filet de sécurité : si le rollback ne fonctionnait pas, la ligne survivrait et
         * pourrait perturber d'autres tests de la suite.
         */
        @AfterEach
        void nettoyer() {
            jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", "erin@example.com");
        }

        @Test
        void repond_503_sans_erreur_de_champ_et_annule_la_creation_du_compte() throws Exception {
            mockMvc.perform(post("/api/registrations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corps("erin@example.com", MOT_DE_PASSE_VALIDE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist());

            // Le rollback de SpringCommandBus doit avoir annulé l'inscription entière.
            assertThat(queryBus.ask(new FindUserByEmail("erin@example.com"))).isEmpty();
        }

        @TestConfiguration(proxyBeanMethods = false)
        static class EchecEnvoiConfiguration {

            @Bean
            @Primary
            NotificationSender notificationSender() {
                return notification -> {
                    throw new MailSendException("échec d'envoi simulé pour le test");
                };
            }
        }
    }
}

package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.application.query.FindUserByEmail;
import xyz.sterenn.secondbrain.users.application.query.UserView;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;

/**
 * Couvre les scénarios d'écriture du ticket de création de compte au niveau HTTP.
 * CSRF est désactivé côté application : aucun jeton à fournir.
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

    @Test
    void cree_le_compte_et_redirige_en_cas_de_succes() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "alice@example.com")
                .param("password", MOT_DE_PASSE_VALIDE))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register?success"));

        Optional<UserView> vue = queryBus.ask(new FindUserByEmail("alice@example.com"));
        assertThat(vue).isPresent();
        assertThat(vue.get().verified()).isFalse();
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_l_email_est_deja_utilise() throws Exception {
        mockMvc.perform(post("/register")
            .param("email", "bob@example.com")
            .param("password", MOT_DE_PASSE_VALIDE));

        mockMvc.perform(post("/register")
                .param("email", "bob@example.com")
                .param("password", MOT_DE_PASSE_VALIDE))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "email"));
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_le_mot_de_passe_est_faible() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "carol@example.com")
                .param("password", "court"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "password"));

        assertThat(queryBus.ask(new FindUserByEmail("carol@example.com"))).isEmpty();
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_l_email_est_mal_forme() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "pas-un-email")
                .param("password", MOT_DE_PASSE_VALIDE))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "email"));
    }

    @Test
    void reaffiche_le_formulaire_avec_une_erreur_si_un_champ_est_vide() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "")
                .param("password", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("registrationForm", "email", "password"));
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
        void reaffiche_le_formulaire_avec_une_erreur_globale_et_annule_la_creation_du_compte()
                throws Exception {
            mockMvc.perform(post("/register")
                    .param("email", "erin@example.com")
                    .param("password", MOT_DE_PASSE_VALIDE))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(result -> {
                    BindingResult bindingResult = (BindingResult) result.getModelAndView()
                        .getModel()
                        .get(BindingResult.MODEL_KEY_PREFIX + "registrationForm");
                    assertThat(bindingResult.getGlobalErrors()).hasSize(1);
                    assertThat(bindingResult.getFieldErrors()).isEmpty();
                });

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

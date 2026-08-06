package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.application.query.FindUserByEmail;
import xyz.sterenn.secondbrain.users.application.query.UserView;

/**
 * Couvre les trois scénarios Gherkin du ticket au niveau HTTP.
 * CSRF est désactivé côté application : aucun jeton à fournir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationControllerTest {

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryBus queryBus;

    @Test
    void affiche_le_formulaire_a_un_visiteur_anonyme() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("registrationForm"));
    }

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
}

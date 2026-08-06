package xyz.sterenn.secondbrain.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;

/**
 * La page d'accueil est statique : rien n'est écrit en base, donc pas de {@code @Transactional}.
 * Testcontainers reste nécessaire pour lever le contexte (Flyway et {@code ddl-auto: validate}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void affiche_la_page_d_accueil_a_un_visiteur_anonyme() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home"));
    }

    @Test
    void propose_un_lien_vers_la_creation_de_compte() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(content().string(containsString("href=\"/register\"")));
    }
}

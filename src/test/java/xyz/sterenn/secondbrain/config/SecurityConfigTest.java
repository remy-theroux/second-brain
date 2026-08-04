package xyz.sterenn.secondbrain.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aucune_authentification_n_est_exigee() throws Exception {
        // Une URL inconnue doit répondre 404 et non 401 : la preuve qu'aucun
        // filtre d'authentification ne s'interpose avant le routage.
        mockMvc.perform(get("/une-url-inexistante"))
            .andExpect(status().isNotFound());
    }

    @Test
    void la_documentation_openapi_reste_accessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }
}

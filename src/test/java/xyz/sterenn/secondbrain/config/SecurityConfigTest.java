package xyz.sterenn.secondbrain.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    void n_exige_aucune_authentification_sur_les_routes_publiques() throws Exception {
        // Une URL inconnue doit répondre 404 et non 401 : la preuve qu'aucun filtre
        // d'authentification ne s'interpose avant le routage des routes publiques.
        mockMvc.perform(get("/une-url-inexistante"))
            .andExpect(status().isNotFound());
    }

    @Test
    void la_documentation_openapi_reste_accessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    void refuse_par_defaut_une_route_inconnue_sous_api() throws Exception {
        // Le refus par défaut sous /api/** doit répondre 401 et non 404 : une future route
        // protégée l'est sans que personne ait eu à la déclarer.
        mockMvc.perform(get("/api/une-route-inconnue"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protege_la_route_de_profil() throws Exception {
        // Le verrou vit dans SecurityConfig, pas dans le contrôleur : c'est ici qu'on le
        // constate, pour qu'un `permitAll()` distrait soit rattrapé par ce test.
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void laisse_la_delivrance_de_jeton_ouverte() throws Exception {
        // Sans jeton, impossible d'en obtenir un : la route de connexion doit rester
        // anonyme. Un 401 ici signifierait une boucle sans issue.
        mockMvc.perform(post("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isBadRequest());
    }
}

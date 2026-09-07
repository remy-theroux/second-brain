package xyz.sterenn.secondbrain.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requires_no_authentication_on_public_routes() throws Exception {
        mockMvc.perform(get("/une-url-inexistante")).andExpect(status().isNotFound());
    }

    @Test
    void keeps_the_openapi_documentation_reachable() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void rejects_an_unknown_route_under_api_by_default() throws Exception {
        mockMvc.perform(get("/api/une-route-inconnue")).andExpect(status().isUnauthorized());
    }

    @Test
    void protects_the_profile_route() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void leaves_account_creation_open() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void leaves_token_issuance_open() throws Exception {
        mockMvc.perform(post("/api/token").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest());
    }
}

package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticateUserTest {

    @Test
    void ne_divulgue_pas_le_mot_de_passe_dans_son_rendu_texte() {
        AuthenticateUser query = new AuthenticateUser("alice@exemple.fr", "chevalpile42");

        assertThat(query.toString()).contains("alice@exemple.fr").doesNotContain("chevalpile42");
    }
}

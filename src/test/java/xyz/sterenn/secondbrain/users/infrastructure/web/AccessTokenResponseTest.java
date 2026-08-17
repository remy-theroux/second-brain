package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessTokenResponseTest {

    @Test
    void ne_divulgue_pas_sa_valeur_dans_son_rendu_texte() {
        AccessTokenResponse reponse = new AccessTokenResponse("eyJ.secret.abc", "Bearer", 3600L);

        assertThat(reponse.toString()).doesNotContain("eyJ.secret.abc");
    }
}

package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessTokenViewTest {

    @Test
    void ne_divulgue_pas_sa_valeur_dans_son_rendu_texte() {
        AccessTokenView vue = new AccessTokenView("eyJ.secret.abc", 3600L);

        assertThat(vue.toString()).doesNotContain("eyJ.secret.abc");
    }
}

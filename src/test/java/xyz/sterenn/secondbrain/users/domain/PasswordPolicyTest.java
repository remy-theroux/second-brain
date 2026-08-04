package xyz.sterenn.secondbrain.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void accepte_un_mot_de_passe_de_douze_caracteres() {
        assertThat(PasswordPolicy.isAcceptable("chevalpile42")).isTrue();
    }

    @Test
    void refuse_un_mot_de_passe_trop_court() {
        assertThat(PasswordPolicy.isAcceptable("chevalpile4")).isFalse();
    }

    @Test
    void accepte_un_mot_de_passe_a_la_longueur_maximale() {
        // 10 + 118 = 128 caractères
        assertThat(PasswordPolicy.isAcceptable("chevalpile" + "9".repeat(118))).isTrue();
    }

    @Test
    void refuse_un_mot_de_passe_trop_long() {
        // 10 + 119 = 129 caractères
        assertThat(PasswordPolicy.isAcceptable("chevalpile" + "9".repeat(119))).isFalse();
    }

    @Test
    void refuse_un_mot_de_passe_de_la_blocklist() {
        assertThat(PasswordPolicy.isAcceptable("motdepasse12")).isFalse();
    }

    @Test
    void refuse_un_mot_de_passe_de_la_blocklist_quelle_que_soit_la_casse() {
        assertThat(PasswordPolicy.isAcceptable("MotDePasse12")).isFalse();
    }

    @Test
    void refuse_un_mot_de_passe_null() {
        assertThat(PasswordPolicy.isAcceptable(null)).isFalse();
    }
}

package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegisterUserTest {

    @Test
    void n_expose_jamais_le_mot_de_passe_dans_sa_representation_textuelle() {
        String texte = new RegisterUser("alice@example.com", "chevalpile42").toString();

        assertThat(texte).doesNotContain("chevalpile42");
        assertThat(texte).contains("alice@example.com");
    }
}

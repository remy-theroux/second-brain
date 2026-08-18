package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerifyAccountTest {

    @Test
    void ne_divulgue_pas_le_jeton_dans_sa_representation_textuelle() {
        VerifyAccount commande = new VerifyAccount("11111111-1111-1111-1111-111111111111", "un-jeton-tres-secret");

        assertThat(commande.toString())
                .doesNotContain("un-jeton-tres-secret")
                .contains("11111111-1111-1111-1111-111111111111");
    }
}

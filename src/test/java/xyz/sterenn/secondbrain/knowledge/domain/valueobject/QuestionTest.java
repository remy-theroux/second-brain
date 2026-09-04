package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;

class QuestionTest {

    @Test
    void refuse_une_question_absente() {
        assertThatThrownBy(() -> new Question(null))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("La question ne peut pas être vide.");
    }

    @Test
    void refuse_une_question_vide() {
        assertThatThrownBy(() -> new Question("")).isInstanceOf(InvalidQuestionException.class);
    }

    @Test
    void refuse_une_question_faite_d_espaces() {
        assertThatThrownBy(() -> new Question("   \n\t ")).isInstanceOf(InvalidQuestionException.class);
    }

    @Test
    void ampute_les_espaces_de_bord() {
        assertThat(new Question("  Qui a signé le rapport ?  ").value()).isEqualTo("Qui a signé le rapport ?");
    }

    @Test
    void deux_ecritures_d_une_meme_question_sont_egales() {
        assertThat(new Question(" Quand ? ")).isEqualTo(new Question("Quand ?"));
    }

    @Test
    void refuse_une_question_plus_longue_que_le_plafond() {
        String trop_longue = "a".repeat(Question.LONGUEUR_MAXIMALE + 1);

        assertThatExceptionOfType(InvalidQuestionException.class)
                .isThrownBy(() -> new Question(trop_longue))
                .withMessageContaining("2 000");
    }

    @Test
    void accepte_une_question_exactement_au_plafond() {
        String au_plafond = "a".repeat(Question.LONGUEUR_MAXIMALE);

        assertThat(new Question(au_plafond).value()).hasSize(Question.LONGUEUR_MAXIMALE);
    }

    @Test
    void mesure_le_plafond_apres_avoir_retire_les_blancs() {
        String au_plafond_une_fois_nettoyee = "  " + "a".repeat(Question.LONGUEUR_MAXIMALE) + "  ";

        assertThat(new Question(au_plafond_une_fois_nettoyee).value()).hasSize(Question.LONGUEUR_MAXIMALE);
    }
}

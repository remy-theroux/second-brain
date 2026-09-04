package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
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
}

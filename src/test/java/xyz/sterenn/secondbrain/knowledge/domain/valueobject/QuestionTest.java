package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;

class QuestionTest {

    @Test
    void rejects_a_missing_question() {
        assertThatThrownBy(() -> new Question(null))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("La question ne peut pas être vide.");
    }

    @Test
    void rejects_an_empty_question() {
        assertThatThrownBy(() -> new Question("")).isInstanceOf(InvalidQuestionException.class);
    }

    @Test
    void rejects_a_question_made_of_whitespace() {
        assertThatThrownBy(() -> new Question("   \n\t ")).isInstanceOf(InvalidQuestionException.class);
    }

    @Test
    void trims_the_surrounding_whitespace() {
        assertThat(new Question("  Qui a signé le rapport ?  ").value()).isEqualTo("Qui a signé le rapport ?");
    }

    @Test
    void two_spellings_of_the_same_question_are_equal() {
        assertThat(new Question(" Quand ? ")).isEqualTo(new Question("Quand ?"));
    }

    @Test
    void rejects_a_question_longer_than_the_ceiling() {
        String tooLong = "a".repeat(Question.MAX_LENGTH + 1);

        assertThatExceptionOfType(InvalidQuestionException.class)
                .isThrownBy(() -> new Question(tooLong))
                .withMessageContaining("2 000");
    }

    @Test
    void accepts_a_question_exactly_at_the_ceiling() {
        String atTheCeiling = "a".repeat(Question.MAX_LENGTH);

        assertThat(new Question(atTheCeiling).value()).hasSize(Question.MAX_LENGTH);
    }

    @Test
    void measures_the_ceiling_after_stripping_the_whitespace() {
        String atTheCeilingOnceTrimmed = "  " + "a".repeat(Question.MAX_LENGTH) + "  ";

        assertThat(new Question(atTheCeilingOnceTrimmed).value()).hasSize(Question.MAX_LENGTH);
    }
}

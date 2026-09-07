package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;

class EmailTest {

    @Test
    void normalises_the_case_and_the_spaces() {
        assertThat(new Email("  Alice@Example.COM  ").value()).isEqualTo("alice@example.com");
    }

    @Test
    void two_identically_normalised_emails_are_equal() {
        assertThat(new Email("Alice@Example.com")).isEqualTo(new Email("alice@example.com"));
    }

    @Test
    void rejects_an_email_without_an_at_sign() {
        assertThatThrownBy(() -> new Email("pas-un-email")).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void rejects_an_email_without_a_top_level_domain() {
        assertThatThrownBy(() -> new Email("alice@example")).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void rejects_a_blank_email() {
        assertThatThrownBy(() -> new Email("   ")).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void rejects_a_null_email() {
        assertThatThrownBy(() -> new Email(null)).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void rejects_a_too_long_email() {
        String tooLong = "a".repeat(310) + "@example.com";
        assertThatThrownBy(() -> new Email(tooLong)).isInstanceOf(InvalidEmailException.class);
    }
}

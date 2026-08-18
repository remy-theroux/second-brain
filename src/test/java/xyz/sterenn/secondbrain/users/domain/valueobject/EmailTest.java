package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;

class EmailTest {

    @Test
    void normalise_la_casse_et_les_espaces() {
        assertThat(new Email("  Alice@Example.COM  ").value()).isEqualTo("alice@example.com");
    }

    @Test
    void deux_emails_normalises_identiques_sont_egaux() {
        assertThat(new Email("Alice@Example.com")).isEqualTo(new Email("alice@example.com"));
    }

    @Test
    void refuse_un_email_sans_arobase() {
        assertThatThrownBy(() -> new Email("pas-un-email")).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_sans_domaine_de_premier_niveau() {
        assertThatThrownBy(() -> new Email("alice@example")).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_vide() {
        assertThatThrownBy(() -> new Email("   ")).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_null() {
        assertThatThrownBy(() -> new Email(null)).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void refuse_un_email_trop_long() {
        String trop_long = "a".repeat(310) + "@example.com";
        assertThatThrownBy(() -> new Email(trop_long)).isInstanceOf(InvalidEmailException.class);
    }
}

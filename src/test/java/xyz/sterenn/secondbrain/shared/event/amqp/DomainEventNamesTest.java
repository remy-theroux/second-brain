package xyz.sterenn.secondbrain.shared.event.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.event.TestEvents;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

class DomainEventNamesTest {

    @Test
    void nomme_par_le_contexte_l_objet_et_le_fait() {
        assertThat(DomainEventNames.of(DocumentUploaded.class)).isEqualTo("knowledge.document.uploaded");
    }

    @Test
    void joint_les_mots_de_l_objet_par_un_tiret() {
        // Le dernier mot est le fait ; tout ce qui précède est l'objet, et l'objet reste un
        // seul segment : la clé garde ses trois segments quel que soit le nombre de mots.
        assertThat(DomainEventNames.of(DocumentTextExtracted.class)).isEqualTo("knowledge.document-text.extracted");
    }

    @Test
    void refuse_un_nom_sans_objet() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(TestEvents.Uploaded.class))
                .withMessageContaining("objet");
    }

    @Test
    void refuse_un_evenement_hors_d_un_contexte_borne() {
        // Ce record est déclaré ici, donc dans shared.event.amqp : `shared` n'est pas un
        // contexte borné, un événement n'a rien à y faire.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(HorsContexte.class))
                .withMessageContaining(HorsContexte.class.getName());
    }

    @Test
    void refuse_une_lambda_ou_une_classe_anonyme() {
        // `DomainEvent` n'a qu'une seule méthode abstraite, donc une lambda compile : c'est
        // le cas à fermer. Ni elle ni une classe anonyme n'ont de nom simple exploitable.
        DomainEvent lambda = () -> Instant.parse("2026-08-25T10:00:00Z");
        DomainEvent anonyme = new DomainEvent() {
            @Override
            public Instant occurredAt() {
                return Instant.parse("2026-08-25T10:00:00Z");
            }
        };

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(lambda.getClass()))
                .withMessageContaining("anonyme");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(anonyme.getClass()))
                .withMessageContaining("anonyme");
    }

    @Test
    void construit_la_table_des_noms_connus() {
        assertThat(DomainEventNames.mappingOf(List.of(DocumentUploaded.class)))
                .containsExactly(java.util.Map.entry("knowledge.document.uploaded", DocumentUploaded.class));
    }

    @Test
    void refuse_deux_classes_du_meme_nom() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DomainEventNames.mappingOf(List.of(DocumentUploaded.class, DocumentUploaded.class)))
                .withMessageContaining("knowledge.document.uploaded");
    }

    record HorsContexte(Instant occurredAt) implements DomainEvent {}
}

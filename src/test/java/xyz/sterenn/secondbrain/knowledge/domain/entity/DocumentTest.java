package xyz.sterenn.secondbrain.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

class DocumentTest {

    private static final UUID PROPRIETAIRE = UUID.randomUUID();
    private static final Checksum EMPREINTE = Checksum.of("contenu".getBytes(StandardCharsets.UTF_8));

    @Test
    void nait_en_attente_de_traitement() {
        Document document = Document.upload(PROPRIETAIRE, "rapport.pdf", DocumentFormat.PDF, EMPREINTE, 12L);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void porte_son_proprietaire_son_nom_son_format_et_son_empreinte() {
        Document document = Document.upload(PROPRIETAIRE, "rapport.pdf", DocumentFormat.PDF, EMPREINTE, 12L);

        assertThat(document.getOwnerId()).isEqualTo(PROPRIETAIRE);
        assertThat(document.getFilename()).isEqualTo("rapport.pdf");
        assertThat(document.getFormat()).isEqualTo(DocumentFormat.PDF);
        assertThat(document.getChecksum()).isEqualTo(EMPREINTE);
        assertThat(document.getSizeBytes()).isEqualTo(12L);
    }

    @Test
    void tronque_un_nom_de_fichier_trop_long_plutot_que_de_refuser_le_contenu() {
        String nomInterminable = "a".repeat(400) + ".pdf";

        Document document = Document.upload(PROPRIETAIRE, nomInterminable, DocumentFormat.PDF, EMPREINTE, 12L);

        assertThat(document.getFilename()).hasSize(Document.MAX_FILENAME_LENGTH);
    }

    @Test
    void refuse_un_document_vide() {
        assertThatThrownBy(() -> Document.upload(PROPRIETAIRE, "vide.txt", DocumentFormat.TEXT, EMPREINTE, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_un_document_sans_proprietaire() {
        assertThatThrownBy(() -> Document.upload(null, "rapport.pdf", DocumentFormat.PDF, EMPREINTE, 12L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_un_document_sans_nom() {
        assertThatThrownBy(() -> Document.upload(PROPRIETAIRE, "   ", DocumentFormat.PDF, EMPREINTE, 12L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_document_extrait_porte_le_statut_extracted_et_aucun_motif() {
        Document document = unDocumentDepose();

        document.markTextExtracted();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void un_document_en_echec_porte_le_statut_failed_et_son_motif() {
        Document document = unDocumentDepose();

        document.markProcessingFailed("Ce document ne contient pas de texte exploitable.");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).isEqualTo("Ce document ne contient pas de texte exploitable.");
    }

    @Test
    void une_extraction_reussie_efface_le_motif_de_l_echec_precedent() {
        Document document = unDocumentDepose();
        document.markProcessingFailed("Un premier échec.");

        document.markTextExtracted();

        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void refuse_un_echec_sans_motif() {
        Document document = unDocumentDepose();

        assertThatThrownBy(() -> document.markProcessingFailed("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motif");
    }

    @Test
    void tronque_un_motif_trop_long_pour_sa_colonne() {
        Document document = unDocumentDepose();

        document.markProcessingFailed("M".repeat(Document.MAX_ERROR_MESSAGE_LENGTH + 42));

        assertThat(document.getErrorMessage()).hasSize(Document.MAX_ERROR_MESSAGE_LENGTH);
    }

    private static Document unDocumentDepose() {
        return Document.upload(PROPRIETAIRE, "rapport.pdf", DocumentFormat.PDF, EMPREINTE, 12L);
    }
}

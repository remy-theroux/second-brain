package xyz.sterenn.secondbrain.knowledge.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDocumentStorageTest {

    private static final byte[] CONTENU = "le contenu d'origine".getBytes(StandardCharsets.UTF_8);

    @Test
    void conserve_puis_relit_un_contenu(@TempDir Path repertoire) {
        FilesystemDocumentStorage storage = new FilesystemDocumentStorage(repertoire.toString());
        UUID document = UUID.randomUUID();

        storage.store(document, CONTENU);

        assertThat(storage.read(document))
                .hasValueSatisfying(relu -> assertThat(relu).isEqualTo(CONTENU));
    }

    @Test
    void cree_le_repertoire_de_destination_s_il_manque(@TempDir Path repertoire) {
        FilesystemDocumentStorage storage = new FilesystemDocumentStorage(
                repertoire.resolve("pas/encore/la").toString());
        UUID document = UUID.randomUUID();

        storage.store(document, CONTENU);

        assertThat(storage.read(document)).isPresent();
    }

    @Test
    void ne_rend_rien_pour_un_document_sans_original(@TempDir Path repertoire) {
        FilesystemDocumentStorage storage = new FilesystemDocumentStorage(repertoire.toString());

        assertThat(storage.read(UUID.randomUUID())).isEmpty();
    }

    @Test
    void efface_un_contenu_conserve(@TempDir Path repertoire) {
        FilesystemDocumentStorage storage = new FilesystemDocumentStorage(repertoire.toString());
        UUID document = UUID.randomUUID();
        storage.store(document, CONTENU);

        storage.delete(document);

        assertThat(storage.read(document)).isEmpty();
    }

    @Test
    void reste_silencieux_en_effacant_ce_qui_n_existe_pas(@TempDir Path repertoire) {
        FilesystemDocumentStorage storage = new FilesystemDocumentStorage(repertoire.toString());

        storage.delete(UUID.randomUUID());

        assertThat(repertoire).isEmptyDirectory();
    }

    @Test
    void refuse_d_ecraser_un_original_deja_conserve(@TempDir Path repertoire) {
        FilesystemDocumentStorage storage = new FilesystemDocumentStorage(repertoire.toString());
        UUID document = UUID.randomUUID();
        storage.store(document, CONTENU);

        assertThatThrownBy(() -> storage.store(document, "autre chose".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
    }
}

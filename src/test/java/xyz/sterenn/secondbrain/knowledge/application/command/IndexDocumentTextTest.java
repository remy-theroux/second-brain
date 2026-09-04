package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest
@Transactional
class IndexDocumentTextTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingEmbeddingPort embeddingPort;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String bucketDesOriginaux;

    @BeforeEach
    void videLaDoublure() {
        embeddingPort.clear();
    }

    @AfterEach
    void videLaDoublureEtLesOriginaux() {
        // La doublure est un singleton du contexte Spring, et le stockage objet ne participe à
        // aucune transaction : @Transactional n'annule ni l'un ni l'autre.
        embeddingPort.clear();
        KnowledgeFixture.videLesOriginaux(s3Client, bucketDesOriginaux);
    }

    @Test
    void range_les_extraits_vectorises_et_marque_le_document_pret() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        List<TextChunk> extraits = textChunkRepository.findByDocumentId(document.getId());
        assertThat(extraits).isNotEmpty();
        assertThat(extraits)
                .allSatisfy(
                        extrait -> assertThat(extrait.getEmbedding().values()).hasSize(EmbeddingPolicy.DIMENSIONS));
        assertThat(relis(document).getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void numerote_les_extraits_dans_l_ordre_du_document() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        List<TextChunk> extraits = textChunkRepository.findByDocumentId(document.getId());
        assertThat(extraits)
                .extracting(TextChunk::getPosition)
                .containsExactlyElementsOf(
                        IntStream.range(0, extraits.size()).boxed().toList());
        List<TextBlock> blocs = textExtractionRepository
                .findByDocumentId(document.getId())
                .orElseThrow()
                .text()
                .blocks();
        assertThat(extraits)
                .extracting(TextChunk::getHeading)
                .containsExactlyElementsOf(
                        blocs.stream().map(TextBlock::getHeading).toList());
        assertThat(extraits.getFirst().getText()).isEqualTo(blocs.getFirst().getText());
    }

    @Test
    void range_sous_chaque_extrait_le_vecteur_de_son_propre_texte() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        List<TextChunk> extraits = textChunkRepository.findByDocumentId(document.getId());
        assertThat(extraits).isNotEmpty();
        for (int position = 0; position < extraits.size(); position++) {
            assertThat(extraits.get(position).getEmbedding()).isEqualTo(RecordingEmbeddingPort.vecteurDuRang(position));
        }
    }

    @Test
    void vectorise_un_texte_prefixe_du_nom_du_document_et_de_sa_section() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        assertThat(embeddingPort.textesRecus()).isNotEmpty().allSatisfy(texte -> assertThat(texte)
                .startsWith("Document: structure.md"));
        assertThat(embeddingPort.textesRecus())
                .anySatisfy(texte -> assertThat(texte).contains("— Section: "));
        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .allSatisfy(extrait -> assertThat(extrait.getText()).doesNotContain("Document: structure.md"));
    }

    @Test
    void une_seconde_indexation_remplace_les_extraits_sans_les_doubler() {
        Document document = unDocumentExtrait("structure.md", Fixtures.STRUCTURE_MD);
        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));
        int premiers = textChunkRepository.findByDocumentId(document.getId()).size();

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textChunkRepository.findByDocumentId(document.getId())).hasSize(premiers);
    }

    @Test
    void laisse_remonter_le_refus_du_service_de_vectorisation() {
        Document document = unDocumentExtrait("notes.txt", Fixtures.BRUT_TXT);
        embeddingPort.tombeEnPanne();

        // Dernier appel du test : le refus marque la transaction englobante rollback-only.
        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId())));
    }

    @Test
    void refuse_un_document_qui_n_appartient_pas_au_demandeur() {
        Document document = unDocumentExtrait("notes.txt", Fixtures.BRUT_TXT);

        // Dernier appel du test, même raison.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(new IndexDocumentText(document.getId(), UUID.randomUUID())));
    }

    private Document unDocumentExtrait(String filename, String fixture) {
        UUID proprietaire = userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
        byte[] contenu = Fixtures.lire(fixture);
        commandBus.dispatch(new UploadDocument(proprietaire, filename, contenu));
        Document document = documentRepository
                .findByOwnerIdAndChecksum(proprietaire, Checksum.of(contenu))
                .orElseThrow();
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));
        return document;
    }

    private Document relis(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }
}

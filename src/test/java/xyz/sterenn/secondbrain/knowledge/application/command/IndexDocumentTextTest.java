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

/**
 * L'indexation par le bus, comme en production — dépôt et extraction compris, sans quoi il n'y
 * aurait rien à découper.
 *
 * <p>{@code @Transactional} : la base est annulée après chaque test, le disque non, d'où le
 * nettoyage en {@code @AfterEach}. Conséquence à connaître : <strong>ce qui est vérifié ici,
 * ce n'est pas le « tout ou rien »</strong> — dans une transaction de test, un rollback du bus
 * ne défait rien de visible. La preuve qu'un Ollama à terre ne laisse aucun extrait derrière
 * lui appartient au test du worker, qui observe de vrais commits.
 */
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

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    @BeforeEach
    void videLaDoublure() {
        embeddingPort.clear();
    }

    @AfterEach
    void videLaDoublureEtLesOriginaux() {
        // La doublure est un singleton partagé par tout le contexte Spring : aucune
        // transaction de test ne la vide, et le drapeau de panne survivrait sinon au test
        // suivant, y compris dans un autre contexte qui importe cette même configuration.
        embeddingPort.clear();
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
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
        // Positions contiguës à partir de zéro : `isSorted()` ne prouverait rien, la lecture
        // étant déjà triée par la requête.
        assertThat(extraits)
                .extracting(TextChunk::getPosition)
                .containsExactlyElementsOf(
                        IntStream.range(0, extraits.size()).boxed().toList());
        // Et l'ordre est celui du document : chaque section de structure.md tient sous le
        // plafond, donc les extraits en suivent les blocs un pour un.
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
        // Le préfixe part au modèle ; la colonne, elle, porte le corps nu. C'est la seule
        // façon de constater les deux d'un coup.
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

    /**
     * Dépose un document par le bus puis en extrait le texte. Le <strong>premier</strong>
     * argument est le nom sous lequel il est déposé — c'est lui qui décide du format —, le
     * <strong>second</strong> le nom d'une fixture, dont le contenu est lu.
     */
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

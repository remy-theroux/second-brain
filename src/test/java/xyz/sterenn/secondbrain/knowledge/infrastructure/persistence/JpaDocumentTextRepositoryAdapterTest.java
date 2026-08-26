package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * On teste le <strong>port</strong>, jamais l'adapter : c'est le contrat du domaine qui doit
 * tenir. Le montage du propriétaire est celui de {@link JpaDocumentRepositoryAdapterTest} —
 * la clé étrangère de {@code knowledge_documents} traverse les deux contextes bornés.
 *
 * <p>La cascade depuis {@code knowledge_documents} n'est pas vérifiée ici, et c'est
 * délibéré : dans une transaction, Hibernate rendrait le {@code DocumentText} depuis son
 * cache de premier niveau sans jamais interroger la base, et le test passerait au vert quelle
 * que soit la migration. Elle l'est par {@code DeleteDocumentCascadeTest}, non transactionnel.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaDocumentTextRepositoryAdapterTest {

    private static final String CORPS =
            "Un texte assez long pour franchir le plancher des cinquante caractères exigé par le domaine.";

    @Autowired
    private DocumentTextRepository documentTextRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void conserve_les_blocs_dans_l_ordre_avec_leur_titre_et_leur_niveau() {
        Document document = unDocumentDepose("hugo@exemple.fr");
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Introduction", 1, CORPS), TextBlock.of("Détail", 2, CORPS), TextBlock.untitled(CORPS)));

        documentTextRepository.save(DocumentText.of(document.getId(), texte, Instant.parse("2026-08-26T10:00:00Z")));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(relu -> {
                    assertThat(relu.getBlocks()).containsExactlyElementsOf(texte.blocks());
                    assertThat(relu.getBlocks())
                            .extracting(TextBlock::getHeading)
                            .containsExactly("Introduction", "Détail", "");
                    assertThat(relu.getBlocks())
                            .extracting(TextBlock::getHeadingLevel)
                            .containsExactly(1, 2, 0);
                    assertThat(relu.getExtractedAt()).isEqualTo(Instant.parse("2026-08-26T10:00:00Z"));
                });
    }

    @Test
    void rend_le_format_du_domaine_tel_qu_il_a_ete_range() {
        Document document = unDocumentDepose("iris@exemple.fr");
        ExtractedText texte = ExtractedText.untitled(CORPS);
        documentTextRepository.save(DocumentText.of(document.getId(), texte, Instant.now()));

        DocumentText relu =
                documentTextRepository.findByDocumentId(document.getId()).orElseThrow();

        assertThat(relu.text()).isEqualTo(texte);
    }

    @Test
    void conserve_un_bloc_bien_plus_long_que_255_caracteres() {
        Document document = unDocumentDepose("jules@exemple.fr");
        String tresLong = CORPS.repeat(200);
        documentTextRepository.save(DocumentText.of(document.getId(), ExtractedText.untitled(tresLong), Instant.now()));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(relu ->
                        assertThat(relu.getBlocks().getFirst().getText()).isEqualTo(tresLong));
    }

    @Test
    void efface_le_texte_d_un_document_et_ses_blocs_avec() {
        Document document = unDocumentDepose("karim@exemple.fr");
        documentTextRepository.save(DocumentText.of(document.getId(), ExtractedText.untitled(CORPS), Instant.now()));

        documentTextRepository.deleteByDocumentId(document.getId());

        assertThat(documentTextRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void reste_muet_quand_aucun_texte_n_a_ete_extrait() {
        assertThat(documentTextRepository.findByDocumentId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void un_second_texte_peut_remplacer_le_premier_apres_effacement() {
        Document document = unDocumentDepose("lea@exemple.fr");
        documentTextRepository.save(DocumentText.of(document.getId(), ExtractedText.untitled(CORPS), Instant.now()));

        documentTextRepository.deleteByDocumentId(document.getId());
        documentTextRepository.save(
                DocumentText.of(document.getId(), ExtractedText.untitled(CORPS + " Deuxième version."), Instant.now()));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(relu ->
                        assertThat(relu.getBlocks().getFirst().getText()).endsWith("Deuxième version."));
    }

    private Document unDocumentDepose(String email) {
        UUID proprietaire = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(proprietaire, "notes.md", DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }
}

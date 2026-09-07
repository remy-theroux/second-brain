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
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaTextExtractionRepositoryAdapterTest {

    private static final String BODY =
            "Un texte assez long pour franchir le plancher des cinquante caractères exigé par le domaine.";

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void keeps_the_blocks_in_order_with_their_heading_and_their_level() {
        Document document = anUploadedDocument("hugo@exemple.fr");
        ExtractedText text = new ExtractedText(List.of(
                TextBlock.of("Introduction", 1, BODY), TextBlock.of("Détail", 2, BODY), TextBlock.untitled(BODY)));

        textExtractionRepository.save(TextExtraction.of(document.getId(), text, Instant.parse("2026-08-26T10:00:00Z")));

        assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getBlocks()).containsExactlyElementsOf(text.blocks());
                    assertThat(reloaded.getBlocks())
                            .extracting(TextBlock::getHeading)
                            .containsExactly("Introduction", "Détail", "");
                    assertThat(reloaded.getBlocks())
                            .extracting(TextBlock::getHeadingLevel)
                            .containsExactly(1, 2, 0);
                    assertThat(reloaded.getExtractedAt()).isEqualTo(Instant.parse("2026-08-26T10:00:00Z"));
                });
    }

    @Test
    void returns_the_domain_format_as_it_was_stored() {
        Document document = anUploadedDocument("iris@exemple.fr");
        ExtractedText text = ExtractedText.untitled(BODY);
        textExtractionRepository.save(TextExtraction.of(document.getId(), text, Instant.now()));

        TextExtraction reloaded =
                textExtractionRepository.findByDocumentId(document.getId()).orElseThrow();

        assertThat(reloaded.text()).isEqualTo(text);
    }

    @Test
    void keeps_a_block_far_longer_than_255_characters() {
        Document document = anUploadedDocument("jules@exemple.fr");
        String veryLong = BODY.repeat(200);
        textExtractionRepository.save(
                TextExtraction.of(document.getId(), ExtractedText.untitled(veryLong), Instant.now()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(reloaded ->
                        assertThat(reloaded.getBlocks().getFirst().getText()).isEqualTo(veryLong));
    }

    @Test
    void deletes_the_text_of_a_document_and_its_blocks_with_it() {
        Document document = anUploadedDocument("karim@exemple.fr");
        textExtractionRepository.save(TextExtraction.of(document.getId(), ExtractedText.untitled(BODY), Instant.now()));

        textExtractionRepository.deleteByDocumentId(document.getId());

        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void stays_silent_when_no_text_has_been_extracted() {
        assertThat(textExtractionRepository.findByDocumentId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void a_second_text_can_replace_the_first_after_deletion() {
        Document document = anUploadedDocument("lea@exemple.fr");
        textExtractionRepository.save(TextExtraction.of(document.getId(), ExtractedText.untitled(BODY), Instant.now()));

        textExtractionRepository.deleteByDocumentId(document.getId());
        textExtractionRepository.save(TextExtraction.of(
                document.getId(), ExtractedText.untitled(BODY + " Deuxième version."), Instant.now()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(reloaded ->
                        assertThat(reloaded.getBlocks().getFirst().getText()).endsWith("Deuxième version."));
    }

    private Document anUploadedDocument(String email) {
        UUID ownerId = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        byte[] bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(ownerId, "notes.md", DocumentFormat.MARKDOWN, Checksum.of(bytes), bytes.length));
    }
}

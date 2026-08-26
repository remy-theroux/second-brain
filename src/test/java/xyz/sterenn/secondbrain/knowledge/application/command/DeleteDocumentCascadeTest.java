package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * La seule vérification que le {@code ON DELETE CASCADE} de la migration V7 fonctionne
 * réellement.
 *
 * <p><strong>Pas de {@code @Transactional} sur la classe, et c'est le tout.</strong> Dans une
 * transaction, Hibernate rendrait le {@code TextExtraction} depuis son cache de premier niveau
 * sans jamais interroger la base : le test passerait au vert quelle que soit la migration, et
 * ne vérifierait rien. D'où le nettoyage explicite en {@code @AfterEach}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeleteDocumentCascadeTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    private final List<String> comptesCrees = new ArrayList<>();

    @AfterEach
    void efface_ce_qui_a_ete_commite() {
        comptesCrees.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        comptesCrees.clear();
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    @Test
    void la_suppression_d_un_document_emporte_son_texte_extrait() {
        Document document = unDocumentDepose();
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();

        commandBus.dispatch(new DeleteDocument(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isEmpty();
        // Les blocs partent avec leur texte : la seconde cascade, que le port ne montre pas.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_text_blocks", Integer.class))
                .isZero();
    }

    private Document unDocumentDepose() {
        String email = UUID.randomUUID() + "@exemple.fr";
        UUID proprietaire = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        comptesCrees.add(email);
        byte[] contenu = Fixtures.lire(Fixtures.BRUT_TXT);
        commandBus.dispatch(new UploadDocument(proprietaire, "notes.txt", contenu));
        return documentRepository
                .findByOwnerIdAndChecksum(proprietaire, Checksum.of(contenu))
                .orElseThrow();
    }
}

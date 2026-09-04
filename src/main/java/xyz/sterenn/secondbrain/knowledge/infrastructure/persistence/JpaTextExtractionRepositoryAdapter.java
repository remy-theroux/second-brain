package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;

@Component
public class JpaTextExtractionRepositoryAdapter implements TextExtractionRepository {

    private final SpringDataTextExtractionRepository springDataTextExtractionRepository;

    JpaTextExtractionRepositoryAdapter(SpringDataTextExtractionRepository springDataTextExtractionRepository) {
        this.springDataTextExtractionRepository = springDataTextExtractionRepository;
    }

    @Override
    public TextExtraction save(TextExtraction textExtraction) {
        return springDataTextExtractionRepository.saveAndFlush(textExtraction);
    }

    @Override
    public Optional<TextExtraction> findByDocumentId(UUID documentId) {
        return springDataTextExtractionRepository.findByDocumentId(documentId);
    }

    /**
     * Le handler efface puis écrit dans la même transaction, et {@code document_id} est
     * {@code UNIQUE} : sans ce flush, Hibernate ordonnerait l'insertion avant la
     * suppression au moment du vidage.
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        springDataTextExtractionRepository.deleteByDocumentId(documentId);
        springDataTextExtractionRepository.flush();
    }
}

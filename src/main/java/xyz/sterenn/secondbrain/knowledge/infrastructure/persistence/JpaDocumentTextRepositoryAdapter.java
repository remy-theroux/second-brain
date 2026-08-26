package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextRepository;

/** Adapter du port {@link DocumentTextRepository}. */
@Component
public class JpaDocumentTextRepositoryAdapter implements DocumentTextRepository {

    private final SpringDataDocumentTextRepository springDataDocumentTextRepository;

    JpaDocumentTextRepositoryAdapter(SpringDataDocumentTextRepository springDataDocumentTextRepository) {
        this.springDataDocumentTextRepository = springDataDocumentTextRepository;
    }

    @Override
    public DocumentText save(DocumentText documentText) {
        return springDataDocumentTextRepository.saveAndFlush(documentText);
    }

    @Override
    public Optional<DocumentText> findByDocumentId(UUID documentId) {
        return springDataDocumentTextRepository.findByDocumentId(documentId);
    }

    /**
     * Le flush n'est pas décoratif : le handler efface puis écrit dans la même transaction,
     * et {@code document_id} est {@code UNIQUE}. Sans lui, Hibernate ordonnerait l'insertion
     * avant la suppression au moment du vidage, et la contrainte se refermerait sur une ligne
     * que l'on venait justement de retirer.
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        springDataDocumentTextRepository.deleteByDocumentId(documentId);
        springDataDocumentTextRepository.flush();
    }
}

package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class ExtractDocumentTextHandler implements CommandHandler<ExtractDocumentText> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final TextExtractionRepository textExtractionRepository;
    private final Map<DocumentFormat, DocumentTextExtractor> extractorsByFormat;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public ExtractDocumentTextHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            TextExtractionRepository textExtractionRepository,
            List<DocumentTextExtractor> documentTextExtractors,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.textExtractionRepository = textExtractionRepository;
        this.extractorsByFormat = indexeParFormat(documentTextExtractors);
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    static Map<DocumentFormat, DocumentTextExtractor> indexeParFormat(
            List<DocumentTextExtractor> documentTextExtractors) {
        Map<DocumentFormat, DocumentTextExtractor> parFormat = new EnumMap<>(DocumentFormat.class);
        for (DocumentTextExtractor extracteur : documentTextExtractors) {
            if (extracteur.format().type() != DocumentType.TEXTUAL) {
                throw new IllegalStateException(
                        "L'extracteur " + extracteur.getClass().getName()
                                + " revendique le format " + extracteur.format()
                                + ", qui n'est pas de typologie textuelle");
            }
            DocumentTextExtractor precedent = parFormat.put(extracteur.format(), extracteur);
            if (precedent != null) {
                throw new IllegalStateException("Deux extracteurs revendiquent le format " + extracteur.format() + " : "
                        + precedent.getClass().getName() + " et "
                        + extracteur.getClass().getName());
            }
        }
        for (DocumentFormat format : DocumentFormat.of(DocumentType.TEXTUAL)) {
            if (!parFormat.containsKey(format)) {
                throw new IllegalStateException(
                        "Aucun extracteur ne sait lire " + format + " : un format accepté au dépôt doit être lisible");
            }
        }
        return Map.copyOf(parFormat);
    }

    @Override
    public void handle(ExtractDocumentText command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        byte[] contenu = documentStorage
                .read(document.getId())
                // La ligne existe, l'original non : voir ADR-0020.
                .orElseThrow(UnreadableDocumentException::new);

        ExtractedText texte = extractorsByFormat.get(document.getFormat()).extract(contenu);

        // Effacer avant d'écrire : AMQP livre au moins une fois et document_id est UNIQUE,
        // une redélivrance échouerait sinon sur la contrainte.
        textExtractionRepository.deleteByDocumentId(document.getId());
        textExtractionRepository.save(TextExtraction.of(document.getId(), texte, clock.instant()));

        document.markTextExtracted();
        documentRepository.save(document);

        domainEventPublisher.publish(new DocumentTextExtracted(
                document.getId(), document.getOwnerId(), texte.blocks().size(), clock.instant()));
    }
}

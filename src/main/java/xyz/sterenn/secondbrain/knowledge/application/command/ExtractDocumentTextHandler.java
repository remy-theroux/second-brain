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
        this.extractorsByFormat = indexedByFormat(documentTextExtractors);
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    static Map<DocumentFormat, DocumentTextExtractor> indexedByFormat(
            List<DocumentTextExtractor> documentTextExtractors) {
        Map<DocumentFormat, DocumentTextExtractor> byFormat = new EnumMap<>(DocumentFormat.class);
        for (DocumentTextExtractor extractor : documentTextExtractors) {
            if (extractor.format().type() != DocumentType.TEXTUAL) {
                throw new IllegalStateException(
                        "L'extracteur " + extractor.getClass().getName()
                                + " revendique le format " + extractor.format()
                                + ", qui n'est pas de typologie textuelle");
            }
            DocumentTextExtractor previous = byFormat.put(extractor.format(), extractor);
            if (previous != null) {
                throw new IllegalStateException("Deux extracteurs revendiquent le format " + extractor.format() + " : "
                        + previous.getClass().getName() + " et "
                        + extractor.getClass().getName());
            }
        }
        for (DocumentFormat format : DocumentFormat.of(DocumentType.TEXTUAL)) {
            if (!byFormat.containsKey(format)) {
                throw new IllegalStateException(
                        "Aucun extracteur ne sait lire " + format + " : un format accepté au dépôt doit être lisible");
            }
        }
        return Map.copyOf(byFormat);
    }

    @Override
    public void handle(ExtractDocumentText command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        byte[] content = documentStorage
                .read(document.getId())
                // The row exists, the original does not: see ADR-0020.
                .orElseThrow(UnreadableDocumentException::new);

        ExtractedText text = extractorsByFormat.get(document.getFormat()).extract(content);

        // Delete before writing: AMQP delivers at least once and document_id is UNIQUE,
        // so a redelivery would otherwise fail on the constraint.
        textExtractionRepository.deleteByDocumentId(document.getId());
        textExtractionRepository.save(TextExtraction.of(document.getId(), text, clock.instant()));

        document.markTextExtracted();
        documentRepository.save(document);

        domainEventPublisher.publish(new DocumentTextExtracted(
                document.getId(), document.getOwnerId(), text.blocks().size(), clock.instant()));
    }
}

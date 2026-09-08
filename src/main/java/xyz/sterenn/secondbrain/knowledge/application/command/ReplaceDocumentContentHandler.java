package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentContentReplaced;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class ReplaceDocumentContentHandler implements CommandHandler<ReplaceDocumentContent> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final TextExtractionRepository textExtractionRepository;
    private final TextChunkRepository textChunkRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public ReplaceDocumentContentHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            TextExtractionRepository textExtractionRepository,
            TextChunkRepository textChunkRepository,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.textExtractionRepository = textExtractionRepository;
        this.textChunkRepository = textChunkRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Override
    public void handle(ReplaceDocumentContent command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        DocumentFormat format = DocumentFormat.fromFilename(command.filename());
        Checksum checksum = Checksum.of(command.content());

        // The most frequent case of a synchronisation, and it must cost nothing: no write, no
        // vectorisation, no announcement.
        if (checksum.equals(document.getChecksum())) {
            return;
        }

        documentRepository.findByOwnerIdAndChecksum(command.ownerId(), checksum).ifPresent(other -> {
            throw new DuplicateDocumentException(other.getId());
        });

        document.replaceContent(command.filename(), format, checksum, command.content().length);
        documentRepository.save(document);

        // The pipeline's own delete-before-write comes after its first failing call, so a
        // refused re-ingestion would leave the previous version's text under the new checksum.
        textExtractionRepository.deleteByDocumentId(document.getId());
        textChunkRepository.deleteByDocumentId(document.getId());

        // The file after the row: see ADR-0020.
        documentStorage.replace(document.getId(), command.content());

        domainEventPublisher.publish(
                new DocumentContentReplaced(document.getId(), document.getOwnerId(), clock.instant()));
    }
}

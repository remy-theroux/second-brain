package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunker;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextIndexed;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class IndexDocumentTextHandler implements CommandHandler<IndexDocumentText> {

    private final DocumentRepository documentRepository;
    private final TextExtractionRepository textExtractionRepository;
    private final TextChunkRepository textChunkRepository;
    private final EmbeddingPort embeddingPort;
    private final RecursiveChunker chunker;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public IndexDocumentTextHandler(
            DocumentRepository documentRepository,
            TextExtractionRepository textExtractionRepository,
            TextChunkRepository textChunkRepository,
            EmbeddingPort embeddingPort,
            TokenCounter tokenCounter,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.textExtractionRepository = textExtractionRepository;
        this.textChunkRepository = textChunkRepository;
        this.embeddingPort = embeddingPort;
        this.chunker = new RecursiveChunker(tokenCounter);
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Override
    public void handle(IndexDocumentText command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        TextExtraction extraction = textExtractionRepository
                .findByDocumentId(document.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Document " + document.getId() + " is announced as extracted but carries no text"));

        List<Chunk> chunks = chunker.chunk(extraction.text());
        // The port returns as many vectors as texts and in the same order: that is what
        // allows pairing them by index.
        List<Embedding> embeddings = embeddingPort.embed(chunks.stream()
                .map(chunk -> chunk.contextualised(document.getFilename()))
                .toList());

        Instant now = clock.instant();
        textChunkRepository.deleteByDocumentId(document.getId());
        textChunkRepository.saveAll(IntStream.range(0, chunks.size())
                .mapToObj(position ->
                        TextChunk.of(document.getId(), position, chunks.get(position), embeddings.get(position), now))
                .toList());

        document.markIndexed();
        documentRepository.save(document);

        domainEventPublisher.publish(
                new DocumentTextIndexed(document.getId(), document.getOwnerId(), chunks.size(), now));
    }
}

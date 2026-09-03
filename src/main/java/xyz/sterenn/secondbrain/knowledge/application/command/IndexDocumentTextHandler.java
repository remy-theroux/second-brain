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

/**
 * Orchestre l'indexation : relecture du document et de son texte, découpage, vectorisation,
 * remplacement des extraits, changement de statut, puis annonce.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch}. <strong>Elle englobe les appels au service de
 * vectorisation</strong>, et c'est un choix : le « tout ou rien » qu'exige le ticket est alors
 * gratuit, c'est le rollback, il n'y a rien à construire. Un Ollama qui tombe au troisième lot
 * ne laisse aucun extrait derrière lui, et le document garde son texte extrait.
 *
 * <p><strong>Le prix est assumé : une connexion PostgreSQL tenue quelques dizaines de secondes
 * par document</strong> — un PDF de trente pages fait une centaine d'extraits, soit quatre
 * lots. Sur une application mono-utilisateur dont le worker consomme en séquence, c'est
 * tenable. C'est précisément le genre de chose qu'on « corrige » spontanément faute de savoir
 * qu'elle a été pesée : les deux découpes en commandes chaînées ou en écritures par lot ont
 * été écartées, la première parce qu'elle rendrait un document découpé mais non vectorisé
 * possible, la seconde parce que c'est l'état partiel que le ticket interdit.
 *
 * <p><strong>Vectoriser avant de toucher à la base.</strong> Transactionnellement c'est
 * indifférent, mais ça se lit mieux, et c'est l'ordre du handler d'extraction : on obtient ce
 * dont on a besoin, puis on écrit.
 *
 * <p><strong>L'effacement avant l'écriture</strong> répond à la redélivrance AMQP, comme à
 * l'extraction : {@code (document_id, chunk_position)} est {@code UNIQUE}.
 *
 * <p>Le {@link RecursiveChunker} est construit ici plutôt qu'injecté : c'est une classe du
 * domaine, elle n'a pas à porter d'annotation Spring, et sa seule dépendance est un port dont
 * ce handler dispose.
 */
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
                // Une anomalie, pas un refus métier : l'événement annonce un texte extrait, et
                // il n'y en a pas. Le consommateur montrera donc le motif générique, ce qui est
                // juste — l'utilisateur n'y peut rien.
                .orElseThrow(() -> new IllegalStateException(
                        "Le document " + document.getId() + " est annoncé extrait mais ne porte aucun texte"));

        List<Chunk> extraits = chunker.chunk(extraction.text());
        // Le port garantit autant de vecteurs que de textes, et dans le même ordre : c'est tout
        // son contrat, et c'est ce qui permet d'apparier par l'indice ci-dessous.
        List<Embedding> vecteurs = embeddingPort.embed(extraits.stream()
                .map(extrait -> extrait.contextualised(document.getFilename()))
                .toList());

        Instant maintenant = clock.instant();
        textChunkRepository.deleteByDocumentId(document.getId());
        textChunkRepository.saveAll(IntStream.range(0, extraits.size())
                .mapToObj(position -> TextChunk.of(
                        document.getId(), position, extraits.get(position), vecteurs.get(position), maintenant))
                .toList());

        document.markIndexed();
        documentRepository.save(document);

        domainEventPublisher.publish(
                new DocumentTextIndexed(document.getId(), document.getOwnerId(), extraits.size(), maintenant));
    }
}

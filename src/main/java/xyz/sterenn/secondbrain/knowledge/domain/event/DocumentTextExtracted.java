package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Le texte d'un document vient d'être extrait et rangé.
 *
 * <p>Comme {@link DocumentUploaded}, il porte des identifiants et non l'état : le
 * consommateur relit, et le texte extrait pèse parfois des centaines de kilo-octets — il n'a
 * rien à faire sur un transport de messages.
 *
 * <p>{@code blockCount} est la seule donnée non identifiante, et elle est là pour une raison
 * précise : elle rend le journal du worker lisible sans requête, et RAG-5 saura d'un coup
 * d'œil s'il a affaire à un document d'une section ou de deux cents.
 *
 * <p>Son nom simple est {@code <Objet><Fait>} : {@code TextExtraction} + {@code Extracted},
 * d'où la clé {@code knowledge.document-text.extracted}, qu'un binding {@code knowledge.#}
 * voit comme tous les autres.
 */
public record DocumentTextExtracted(UUID documentId, UUID ownerId, int blockCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextExtracted {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
        if (blockCount <= 0) {
            throw new IllegalArgumentException("Une extraction sans bloc n'a rien à annoncer");
        }
    }
}

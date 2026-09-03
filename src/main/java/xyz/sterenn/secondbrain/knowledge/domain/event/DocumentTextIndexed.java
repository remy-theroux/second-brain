package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Les extraits d'un document viennent d'être vectorisés et rangés.
 *
 * <p>Comme ses aînés, il porte des identifiants et non l'état : le consommateur relit, et une
 * centaine de vecteurs de mille dimensions n'a rien à faire sur un transport de messages.
 *
 * <p>{@code chunkCount} est la seule donnée non identifiante, pour la même raison que
 * {@code blockCount} l'était : elle rend le journal du worker lisible sans requête.
 *
 * <p>Son nom simple est {@code <Objet><Fait>} : {@code DocumentText} + {@code Indexed}, d'où
 * la clé {@code knowledge.document-text.indexed}, qu'un binding {@code knowledge.#} voit
 * comme tous les autres. <strong>Personne n'en fait rien aujourd'hui</strong> : il est annoncé
 * parce qu'une étape franchie s'annonce, et le seul consommateur le journalise. RAG-8 aura de
 * quoi s'y accrocher.
 */
public record DocumentTextIndexed(UUID documentId, UUID ownerId, int chunkCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextIndexed {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("Une indexation sans extrait n'a rien à annoncer");
        }
    }
}

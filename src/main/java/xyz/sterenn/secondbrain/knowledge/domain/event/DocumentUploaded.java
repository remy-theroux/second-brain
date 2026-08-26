package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Un document vient d'être déposé : sa ligne est écrite, son original conservé.
 *
 * <p>L'événement porte l'identifiant — ce que le consommateur relira — et le propriétaire,
 * pour router ou journaliser par compte sans relire. Rien d'autre : ni nom, ni format, ni
 * empreinte. Il dit <em>qu'il</em> s'est passé quelque chose, pas <em>quoi</em> en détail ;
 * le document en base fait foi.
 *
 * <p>Voyage en JSON sur le transport : les trois composants sont des types que Jackson lit
 * et écrit sans configuration, et le record se désérialise par ses paramètres.
 */
public record DocumentUploaded(UUID documentId, UUID ownerId, Instant occurredAt) implements DomainEvent {

    public DocumentUploaded {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
    }
}

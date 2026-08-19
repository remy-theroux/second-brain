package xyz.sterenn.secondbrain.knowledge.application.query;

import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

/**
 * Projection de lecture d'un document, telle que la liste l'affiche : de quoi reconnaître
 * un dépôt et savoir où il en est.
 *
 * <p>Ni empreinte ni taille : la première n'apprend rien à un humain, la seconde n'a pas
 * encore d'écran qui la demande. Une projection grandit au rythme des besoins, pas de
 * l'agrégat.
 */
public record DocumentView(UUID id, String filename, DocumentStatus status, Instant createdAt) {

    /** Seule conversion depuis l'agrégat, partagée par les handlers qui lisent un document. */
    public static DocumentView of(Document document) {
        return new DocumentView(
                document.getId(), document.getFilename(), document.getStatus(), document.getCreatedAt());
    }
}

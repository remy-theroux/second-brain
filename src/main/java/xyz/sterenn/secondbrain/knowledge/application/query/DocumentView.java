package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

/**
 * Projection de lecture d'un document, telle que la liste l'affiche : de quoi reconnaître un
 * dépôt, savoir où il en est, et — quand ça a mal tourné — pourquoi.
 *
 * <p>Ni empreinte ni taille : la première n'apprend rien à un humain, la seconde n'a pas
 * encore d'écran qui la demande. Une projection grandit au rythme des besoins, pas de
 * l'agrégat.
 *
 * <p>{@code errorMessage} est omis quand il est nul plutôt qu'envoyé à {@code null} : la
 * liste porte toutes les lignes d'une base de connaissance, et la quasi-totalité n'a rien à
 * expliquer. C'est le seul champ optionnel de cette projection, d'où l'annotation sur lui et
 * non sur le record — la forme des autres champs ne se négocie pas.
 *
 * <p>Le message vient du serveur et est affichable tel quel : le front ne le réécrit pas.
 */
public record DocumentView(
        UUID id,
        String filename,
        DocumentStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        Instant createdAt) {

    /** Seule conversion depuis l'agrégat, partagée par les handlers qui lisent un document. */
    public static DocumentView of(Document document) {
        return new DocumentView(
                document.getId(),
                document.getFilename(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getCreatedAt());
    }
}

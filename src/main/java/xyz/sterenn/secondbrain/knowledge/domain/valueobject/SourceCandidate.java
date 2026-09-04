package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record SourceCandidate(UUID documentId, String filename, int position, String heading, String text) {

    public SourceCandidate {
        Objects.requireNonNull(documentId, "Le document dont cet extrait provient est obligatoire");
        Objects.requireNonNull(filename, "Le nom du document est obligatoire");
        Objects.requireNonNull(heading, "Le titre de section est obligatoire, vide s'il n'y en a pas");
        Objects.requireNonNull(text, "Le texte de l'extrait est obligatoire");
    }
}

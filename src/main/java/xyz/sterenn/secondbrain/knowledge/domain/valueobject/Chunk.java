package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;

public record Chunk(String heading, String text) {

    public Chunk {
        Objects.requireNonNull(heading, "Le titre de section est obligatoire, vide s'il n'y en a pas");
        Objects.requireNonNull(text, "Le texte de l'extrait est obligatoire");
        heading = heading.strip();
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Un extrait sans texte n'en est pas un : il ne se construit pas");
        }
    }

    public String contextualised(String filename) {
        Objects.requireNonNull(filename, "Le nom du document est obligatoire");
        String prefixe =
                heading.isEmpty() ? "Document: " + filename : "Document: " + filename + " — Section: " + heading;
        return prefixe + "\n\n" + text;
    }
}

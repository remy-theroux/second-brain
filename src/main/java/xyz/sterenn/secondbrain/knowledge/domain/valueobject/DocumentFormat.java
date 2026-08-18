package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnsupportedDocumentFormatException;

/**
 * Formats de document que la base de connaissance sait accueillir.
 *
 * <p>La liste est fermée et vit ici, pas dans un contrôleur : décider ce qui peut entrer
 * dans la base de connaissance est une règle métier. Un format s'ajoute en ajoutant une
 * constante, et le message de refus se met à jour tout seul — il est construit à partir de
 * l'énumération, jamais recopié à la main.
 *
 * <p>Le format se déduit de l'extension, faute de mieux : le type MIME annoncé par le
 * navigateur est déclaratif, donc pas plus fiable, et renifler le contenu supposerait de
 * savoir déjà le lire. L'extraction, elle, vérifiera qu'un {@code .pdf} en est un.
 */
public enum DocumentFormat {
    PDF(".pdf"),
    MARKDOWN(".md"),
    TEXT(".txt"),
    DOCX(".docx");

    private final String extension;

    DocumentFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    /**
     * Reconnaît le format d'un fichier à son extension, insensible à la casse.
     *
     * @throws UnsupportedDocumentFormatException si l'extension n'est pas reconnue — son
     *         message énonce les formats acceptés, et il est affichable tel quel
     */
    public static DocumentFormat fromFilename(String filename) {
        String normalise = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> normalise.endsWith(format.extension))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentFormatException(acceptedExtensions()));
    }

    /** Les extensions acceptées, dans l'ordre de déclaration, pour un message lisible. */
    public static String acceptedExtensions() {
        return Arrays.stream(values()).map(DocumentFormat::extension).collect(Collectors.joining(", "));
    }
}

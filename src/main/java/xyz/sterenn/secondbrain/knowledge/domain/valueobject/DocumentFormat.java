package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.List;
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
 *
 * <p><strong>Chaque format déclare sa typologie</strong> ({@link DocumentType}) : c'est
 * elle qui dit comment le document se découpe, donc quel traitement l'attend et quelles
 * tables le reçoivent. Ajouter une constante sans la nommer ne compile pas — le
 * constructeur l'exige.
 */
public enum DocumentFormat {
    PDF(".pdf", DocumentType.TEXTUAL),
    MARKDOWN(".md", DocumentType.TEXTUAL),
    TEXT(".txt", DocumentType.TEXTUAL),
    DOCX(".docx", DocumentType.TEXTUAL);

    private final String extension;
    private final DocumentType type;

    DocumentFormat(String extension, DocumentType type) {
        this.extension = extension;
        this.type = type;
    }

    public String extension() {
        return extension;
    }

    /** La typologie de ce format : comment un document de ce format se découpe. */
    public DocumentType type() {
        return type;
    }

    /**
     * Les formats d'une typologie, dans l'ordre de déclaration.
     *
     * <p>C'est cette méthode qui permet à un traitement de ne réclamer que ce qui le
     * concerne : l'extraction de texte exige un extracteur pour chaque format
     * {@link DocumentType#TEXTUAL}, et n'a rien à dire des autres.
     */
    public static List<DocumentFormat> of(DocumentType type) {
        return Arrays.stream(values()).filter(format -> format.type == type).toList();
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

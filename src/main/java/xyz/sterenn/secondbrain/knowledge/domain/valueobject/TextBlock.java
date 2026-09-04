package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.Objects;

/** Voir ADR-0002 : l'écart qui autorise les annotations JPA dans le domaine. */
@Embeddable
public class TextBlock {

    public static final int MAX_HEADING_LENGTH = 255;

    public static final int MAX_HEADING_LEVEL = 6;

    @Column(nullable = false, length = MAX_HEADING_LENGTH)
    private String heading;

    @Column(name = "heading_level", nullable = false)
    private int headingLevel;

    // columnDefinition explicite : sans lui, Hibernate attendrait un varchar(255) et
    // `ddl-auto: validate` refuserait de démarrer contre une colonne `text`.
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    protected TextBlock() {}

    private TextBlock(String heading, int headingLevel, String text) {
        this.heading = heading;
        this.headingLevel = headingLevel;
        this.text = text;
    }

    public static TextBlock of(String heading, int headingLevel, String text) {
        String titre = normaliseHeading(heading);
        String corps = normalise(text);
        if (corps.isEmpty()) {
            throw new IllegalArgumentException("Un bloc sans texte n'en est pas un : il ne se construit pas");
        }
        if (!titre.isEmpty() && (headingLevel < 1 || headingLevel > MAX_HEADING_LEVEL)) {
            throw new IllegalArgumentException(
                    "Le niveau d'un titre va de 1 à " + MAX_HEADING_LEVEL + ", reçu : " + headingLevel);
        }
        return new TextBlock(titre, titre.isEmpty() ? 0 : headingLevel, corps);
    }

    public static TextBlock untitled(String text) {
        return of("", 0, text);
    }

    static String normalise(String brut) {
        if (brut == null) {
            return "";
        }
        return Normalizer.normalize(brut, Normalizer.Form.NFC)
                .replace('\uFEFF', ' ') // marque d'ordre des octets, en tête d'un fichier UTF-8
                .replace('\u00A0', ' ') // espace insécable : un espace, pas rien — l'effacer collerait les mots
                .replace("\u0000", "") // octet nul : un PDF mal formé en sème, et PostgreSQL le refuse
                .replace("\u00AD", "") // trait d'union conditionnel : il couperait les mots
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+(?=\\n)", "")
                .replaceAll("\n{3,}", "\n\n") // la frontière de paragraphe survit, la mise en page non
                .strip();
    }

    private static String normaliseHeading(String brut) {
        String titre = normalise(brut).replaceAll("\\s+", " ").strip();
        return titre.length() > MAX_HEADING_LENGTH
                ? titre.substring(0, MAX_HEADING_LENGTH).strip()
                : titre;
    }

    public String getHeading() {
        return heading;
    }

    public int getHeadingLevel() {
        return headingLevel;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof TextBlock bloc
                && headingLevel == bloc.headingLevel
                && Objects.equals(heading, bloc.heading)
                && Objects.equals(text, bloc.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(heading, headingLevel, text);
    }

    @Override
    public String toString() {
        return "TextBlock[heading=" + heading + ", level=" + headingLevel + ", " + text.length() + " caractères]";
    }
}

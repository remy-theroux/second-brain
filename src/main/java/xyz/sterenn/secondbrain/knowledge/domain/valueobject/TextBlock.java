package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.Objects;

/**
 * Un bloc de texte extrait d'un document, rattaché au titre de sa section.
 *
 * <p><strong>Un bloc est une section, pas un paragraphe.</strong> C'est ce que tranche le
 * second scénario du ticket : un document sans titre rend « un unique bloc contenant tout
 * le texte », or un texte sans titre compte bien plusieurs paragraphes. Voir ADR-0024.
 *
 * <p>Le titre est vide et son niveau nul quand le document n'en porte pas — jamais
 * {@code null} : un consommateur qui préfixe ses extraits n'a pas à distinguer deux formes
 * d'absence.
 *
 * <p>{@code headingLevel} n'a aujourd'hui aucun consommateur. Il est conservé parce qu'il
 * est la seule information permettant plus tard de reconstruire un chemin de section
 * (« Chapitre 1 &gt; Introduction ») sans réextraire toute la base : le niveau, l'extraction
 * est seule à le connaître, le chemin se recalcule à tout moment.
 *
 * <p>{@code @Embeddable} dans le domaine : c'est ADR-0002 — les entités JPA vivent dans le
 * domaine, sans classe miroir ni mapper — étendu à un objet-valeur possédé par une entité.
 * La position dans le document n'est <em>pas</em> un champ : elle appartient à la liste, et
 * c'est {@code @OrderColumn} qui la porte côté {@code DocumentText}.
 */
@Embeddable
public class TextBlock {

    /** Ce que la colonne accepte, et bien au-delà de ce qu'un titre lisible réclame. */
    public static final int MAX_HEADING_LENGTH = 255;

    /** Six niveaux, comme HTML et comme les styles Heading1..6 de Word. */
    public static final int MAX_HEADING_LEVEL = 6;

    @Column(nullable = false, length = MAX_HEADING_LENGTH)
    private String heading;

    @Column(name = "heading_level", nullable = false)
    private int headingLevel;

    // columnDefinition explicite : sans lui, Hibernate attendrait un varchar(255) et
    // `ddl-auto: validate` refuserait de démarrer contre une colonne `text`.
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    protected TextBlock() {
        // requis par JPA
    }

    private TextBlock(String heading, int headingLevel, String text) {
        this.heading = heading;
        this.headingLevel = headingLevel;
        this.text = text;
    }

    /**
     * @throws IllegalArgumentException si le texte est vide une fois normalisé, ou si un
     *     titre renseigné porte un niveau hors de 1 à {@value #MAX_HEADING_LEVEL}. C'est une
     *     erreur de programmation d'un extracteur, pas un refus métier : un document sans
     *     texte se refuse à l'échelle du document, par {@link ExtractedText}.
     */
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

    /** Le document ne portait pas de titre à cet endroit : niveau nul, titre vide. */
    public static TextBlock untitled(String text) {
        return of("", 0, text);
    }

    /**
     * Normalise un texte extrait, sans jamais toucher à ce qui porte du sens.
     *
     * <p>Les fins de ligne sont ramenées à {@code \n}, les espaces de fin de ligne effacés,
     * et toute suite de trois sauts de ligne ou plus ramenée à deux : <strong>la frontière
     * de paragraphe survit, la mise en page non</strong>. C'est cette double ligne que RAG-5
     * cherchera pour découper.
     *
     * <p>NFC parce que deux extracteurs peuvent rendre le même « é » sous deux formes, et
     * que deux blocs identiques doivent être égaux. Quatre caractères invisibles sont
     * traités à part : la marque d'ordre des octets ({@code U+FEFF}) et l'espace insécable
     * ({@code U+00A0}) deviennent des espaces — les effacer collerait les mots —, tandis que
     * l'octet nul ({@code U+0000}, qu'un PDF mal formé sème) et le trait d'union conditionnel
     * ({@code U+00AD}, qui couperait les mots) disparaissent.
     *
     * <p>Package-private : {@link ExtractedTextBuilder} en a besoin pour décider si une
     * section a un corps, avant de tenter de construire un bloc qui serait refusé.
     */
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
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    /** Un titre tient sur une ligne : ses blancs sont aplatis, sa longueur bornée. */
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

    /** Volontairement sans le corps : un bloc pèse parfois plusieurs milliers de caractères. */
    @Override
    public String toString() {
        return "TextBlock[heading=" + heading + ", level=" + headingLevel + ", " + text.length() + " caractères]";
    }
}

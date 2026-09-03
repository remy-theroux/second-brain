package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;

/**
 * Un extrait de document : le corps d'un morceau de section, et le titre de la section d'où
 * il vient.
 *
 * <p>Objet-valeur, exactement comme {@link ExtractedText} l'est pour l'extraction : la
 * logique pure de découpage produit ceci, et c'est le handler qui en fait des entités
 * {@code TextChunk}. Le découpage n'a pas à savoir qu'il existe une base, et un objet-valeur
 * se compare par ses champs — ce qui rend les assertions de test lisibles.
 *
 * <p><strong>La position n'est pas un champ.</strong> Elle appartient à la liste, comme celle
 * d'un {@link TextBlock} appartient à l'{@code @OrderColumn} de son extraction : un extrait
 * sorti de son document reste le même extrait.
 *
 * <p><strong>Le niveau de titre non plus.</strong> Le préfixe n'en a que faire, et le niveau
 * reste lisible dans l'extraction, qui n'est jamais effacée : reconstruire plus tard un
 * chemin de section (« Chapitre 1 &gt; Introduction ») se fera depuis là, sans avoir à le
 * recopier dans chaque extrait.
 */
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

    /**
     * Le texte tel qu'il part au service de vectorisation : précédé de ce qui dit d'où il
     * vient.
     *
     * <p><strong>C'est la seule méthode qui connaisse la forme du préfixe</strong>, et elle
     * servira aussi à alimenter le prompt de RAG-9. Ce qui est <em>stocké</em>, en revanche,
     * est le corps nu : un extrait préfixé montré tel quel à l'écran est du balisage sous les
     * yeux, changer la forme du préfixe plus tard ne doit pas demander de réécrire la base,
     * et la provenance est déjà dite par les colonnes {@code heading} et {@code document_id}
     * aussi bien que par une chaîne recopiée.
     *
     * <p>Ce que ça suppose, et qui est vrai aujourd'hui : <strong>aucune route ne renomme un
     * document.</strong> L'identité d'un document est son empreinte, son nom n'est qu'une
     * étiquette. Le jour où un renommage existerait, la chaîne recalculée cesserait de
     * correspondre au vecteur stocké — ce serait à revectoriser.
     */
    public String contextualised(String filename) {
        Objects.requireNonNull(filename, "Le nom du document est obligatoire");
        String prefixe =
                heading.isEmpty() ? "Document: " + filename : "Document: " + filename + " — Section: " + heading;
        return prefixe + "\n\n" + text;
    }
}

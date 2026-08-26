package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Devine les titres d'un PDF à la taille de police, faute de sommaire. Voir ADR-0027.
 *
 * <p>La règle tient en trois conditions, et aucune ne suffit seule : une ligne est un titre
 * si elle n'est pas vide, si elle est courte, et si elle est écrite nettement plus grand que
 * le corps du document. Le seuil de 15 % écarte les demi-points d'écart d'une même police ;
 * la borne de longueur écarte la citation mise en avant, qui est grande mais bavarde.
 *
 * <p>Le corps, lui, n'est pas la taille la plus fréquente <em>ligne à ligne</em> mais celle
 * qui porte <strong>le plus de caractères</strong> : un document de trente titres et de
 * quarante lignes de corps ferait mentir le décompte par lignes, jamais celui par
 * caractères.
 *
 * <p>Classe utilitaire et non composant : elle n'a aucune dépendance et se teste sur des
 * lignes fabriquées à la main, sans PDF ni Spring.
 */
final class HeadingHeuristic {

    /** Au-delà de 15 % de plus que le corps, c'est un titre. En deçà, un écart de police. */
    private static final float HEADING_RATIO = 1.15f;

    /** Plus long que ça, c'est une phrase mise en avant, pas un titre. */
    private static final int MAX_HEADING_CHARACTERS = 120;

    private HeadingHeuristic() {
        // heuristique, pas un objet
    }

    static List<Section> decouper(List<TextLine> lignes) {
        if (lignes.isEmpty()) {
            return List.of();
        }
        float tailleDuCorps = tailleDuCorps(lignes);
        List<Float> taillesDeTitre = taillesDeTitre(lignes, tailleDuCorps);

        List<Section> sections = new ArrayList<>();
        String titre = "";
        int niveau = 0;
        StringBuilder corps = new StringBuilder();

        for (TextLine ligne : lignes) {
            if (estUnTitre(ligne, tailleDuCorps)) {
                ajoute(sections, titre, niveau, corps);
                titre = ligne.text();
                niveau = Math.min(taillesDeTitre.indexOf(arrondie(ligne.fontSize())) + 1, TextBlock.MAX_HEADING_LEVEL);
                corps.setLength(0);
            } else {
                // Une ligne, pas un paragraphe : PDFTextStripper ne sait pas les distinguer,
                // et deviner leur frontière à l'indentation serait un pari sur la mise en
                // page. RAG-5 découpera cette section à la phrase (ADR-0027).
                corps.append(ligne.text()).append('\n');
            }
        }
        ajoute(sections, titre, niveau, corps);
        return sections;
    }

    /**
     * Une section sans corps n'en est pas une : un document qui s'ouvre sur un titre n'a pas
     * de préambule, et deux titres qui se suivent ne délimitent rien.
     *
     * <p>{@code ExtractedTextBuilder} écarterait de toute façon ces sections plus loin ; les
     * écarter ici aussi est ce qui rend la sortie de {@link #decouper} lisible seule, donc
     * vérifiable sans passer par le domaine.
     */
    private static void ajoute(List<Section> sections, String titre, int niveau, StringBuilder corps) {
        if (!corps.toString().isBlank()) {
            sections.add(new Section(titre, niveau, corps.toString()));
        }
    }

    private static float tailleDuCorps(List<TextLine> lignes) {
        Map<Float, Integer> caracteresParTaille = new HashMap<>();
        for (TextLine ligne : lignes) {
            caracteresParTaille.merge(arrondie(ligne.fontSize()), ligne.text().length(), Integer::sum);
        }
        return caracteresParTaille.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0f);
    }

    /** De la plus grande à la plus petite : l'indice dans cette liste donne le niveau. */
    private static List<Float> taillesDeTitre(List<TextLine> lignes, float tailleDuCorps) {
        return lignes.stream()
                .filter(ligne -> estUnTitre(ligne, tailleDuCorps))
                .map(ligne -> arrondie(ligne.fontSize()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static boolean estUnTitre(TextLine ligne, float tailleDuCorps) {
        return !ligne.text().isBlank()
                && ligne.text().strip().length() <= MAX_HEADING_CHARACTERS
                && arrondie(ligne.fontSize()) > tailleDuCorps * HEADING_RATIO;
    }

    /** Au demi-point : deux glyphes d'une même police diffèrent parfois de quelques centièmes. */
    private static float arrondie(float taille) {
        return Math.round(taille * 2f) / 2f;
    }
}

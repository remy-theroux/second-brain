package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/** Devine les titres d'un PDF à la taille de police, faute de sommaire. Voir ADR-0027. */
final class HeadingHeuristic {

    private static final float HEADING_RATIO = 1.15f;

    private static final int MAX_HEADING_CHARACTERS = 120;

    private HeadingHeuristic() {}

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
                // Une ligne et non un paragraphe : PDFTextStripper ne distingue pas les
                // frontières de paragraphe (ADR-0027).
                corps.append(ligne.text()).append('\n');
            }
        }
        ajoute(sections, titre, niveau, corps);
        return sections;
    }

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

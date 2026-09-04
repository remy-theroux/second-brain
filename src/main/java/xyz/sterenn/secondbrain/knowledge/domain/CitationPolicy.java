package xyz.sterenn.secondbrain.knowledge.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La syntaxe de citation, et sa seule source : {@code PromptBuilder} l'énonce au modèle,
 * l'analyse la relit. Les deux côtés ne peuvent pas diverger.
 */
public final class CitationPolicy {

    public static final String BALISE_OUVRANTE = "<extrait";
    public static final String BALISE_FERMANTE = "</extrait>";

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    private CitationPolicy() {}

    public static List<Integer> citations(String texte) {
        Set<Integer> vues = new LinkedHashSet<>();
        Matcher chercheur = CITATION.matcher(texte);
        while (chercheur.find()) {
            vues.add(Integer.parseInt(chercheur.group(1)));
        }
        return List.copyOf(vues);
    }

    public static OptionalInt endOfFirstValidCitation(String texte, IntPredicate connu) {
        Matcher chercheur = CITATION.matcher(texte);
        while (chercheur.find()) {
            if (connu.test(Integer.parseInt(chercheur.group(1)))) {
                return OptionalInt.of(chercheur.end());
            }
        }
        return OptionalInt.empty();
    }
}

package xyz.sterenn.secondbrain.knowledge.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The citation syntax, and its single source: {@code PromptBuilder} states it to the model,
 * parsing reads it back. The two sides cannot drift apart.
 */
public final class CitationPolicy {

    public static final String OPENING_MARKER = "<extrait";
    public static final String CLOSING_MARKER = "</extrait>";

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    private CitationPolicy() {}

    public static List<Integer> citations(String text) {
        Set<Integer> seen = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(text);
        while (matcher.find()) {
            seen.add(Integer.parseInt(matcher.group(1)));
        }
        return List.copyOf(seen);
    }

    public static OptionalInt endOfFirstValidCitation(String text, IntPredicate known) {
        Matcher matcher = CITATION.matcher(text);
        while (matcher.find()) {
            if (known.test(Integer.parseInt(matcher.group(1)))) {
                return OptionalInt.of(matcher.end());
            }
        }
        return OptionalInt.empty();
    }
}

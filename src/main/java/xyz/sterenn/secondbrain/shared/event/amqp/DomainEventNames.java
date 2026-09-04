package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public final class DomainEventNames {

    private static final String ROOT = "xyz.sterenn.secondbrain";
    private static final Set<String> NOT_A_CONTEXT = Set.of("shared", "config");

    // Coupe avant une majuscule qui suit une minuscule ou un chiffre, et avant la dernière
    // majuscule d'une suite : un acronyme reste un seul mot (`PDFExtracted` → `PDF`, `Extracted`).
    private static final String WORD_BOUNDARY = "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])";

    private DomainEventNames() {}

    public static String of(Class<? extends DomainEvent> type) {
        // `DomainEvent` n'a qu'une méthode abstraite : une lambda compile, et son nom simple est
        // vide ou synthétique — elle voyagerait sous un nom que rien ne peut redésérialiser.
        if (type.isAnonymousClass()
                || type.isLocalClass()
                || type.isSynthetic()
                || type.getSimpleName().isEmpty()) {
            throw new IllegalArgumentException(
                    type.getName() + " doit être un record nommé, pas une classe anonyme ou une lambda");
        }
        String pkg = type.getPackageName();
        if (!pkg.startsWith(ROOT + ".")) {
            throw new IllegalArgumentException(type.getName() + " n'est pas dans un contexte borné de " + ROOT);
        }
        String context = pkg.substring(ROOT.length() + 1).split("\\.")[0];
        if (NOT_A_CONTEXT.contains(context)) {
            throw new IllegalArgumentException(
                    type.getName() + " est dans " + context + ", qui n'est pas un contexte borné");
        }
        List<String> words = Arrays.stream(type.getSimpleName().split(WORD_BOUNDARY))
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
        if (words.size() < 2) {
            throw new IllegalArgumentException(
                    type.getName() + " doit se nommer <Objet><Fait> : un seul mot ne désigne aucun objet");
        }
        String object = String.join("-", words.subList(0, words.size() - 1));
        String fact = words.getLast();
        return context + "." + object + "." + fact;
    }

    public static Map<String, Class<?>> mappingOf(List<Class<? extends DomainEvent>> types) {
        Map<String, Class<?>> mapping = new HashMap<>();
        for (Class<? extends DomainEvent> type : types) {
            String name = of(type);
            Class<?> previous = mapping.put(name, type);
            if (previous != null) {
                throw new IllegalStateException("Deux événements portent le nom " + name + " : " + previous.getName()
                        + " et " + type.getName());
            }
        }
        return mapping;
    }
}

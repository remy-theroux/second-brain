package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Le nom d'un événement sur le transport : {@code <contexte>.<objet>.<fait>}, soit
 * {@code knowledge.document.uploaded} pour
 * {@code xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded}.
 *
 * <p>Le contexte est le premier segment de package sous la racine du projet. L'objet et le
 * fait viennent du nom simple de la classe, découpé sur ses majuscules : le dernier mot est
 * le fait, tout ce qui précède est l'objet, dont les mots sont joints par un tiret pour que
 * la clé garde toujours trois segments — {@code DocumentTextExtracted} donne
 * {@code knowledge.document-text.extracted}, et un binding {@code knowledge.*.*} le voit.
 * Un nom d'un seul mot n'a pas d'objet : il est refusé.
 *
 * <p>Ce nom sert de clé de routage sur l'exchange et d'en-tête de type sur le message —
 * jamais le nom qualifié de la classe : renommer un package ne casse pas les messages en
 * vol, et le nom se lit dans la console du broker. Il est dérivé ici, dans l'adapter, pour
 * que le domaine ne nomme rien (spec, décision 4).
 *
 * <p>{@code shared} et {@code config} ne sont pas des contextes bornés : un événement qui y
 * vivrait est refusé, il n'appartient à personne.
 */
public final class DomainEventNames {

    private static final String ROOT = "xyz.sterenn.secondbrain";
    private static final Set<String> NOT_A_CONTEXT = Set.of("shared", "config");

    // Coupe avant chaque majuscule qui suit une minuscule ou un chiffre, et avant la dernière
    // majuscule d'une suite de majuscules : un acronyme reste un seul mot
    // (`PDFExtracted` → `PDF`, `Extracted`).
    private static final String WORD_BOUNDARY = "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])";

    private DomainEventNames() {
        // classe utilitaire
    }

    /**
     * @throws IllegalArgumentException si la classe n'est pas un record nommé d'un contexte
     *     borné du projet, ou si son nom ne porte pas au moins un objet et un fait
     */
    public static String of(Class<? extends DomainEvent> type) {
        // `DomainEvent` n'a qu'une méthode abstraite : une lambda compile, et son nom simple
        // est vide ou synthétique. Elle prendrait le nom de son package englobant et
        // voyagerait sous un nom que rien ne peut redéserialiser. Refusé ici, à la
        // publication, plutôt qu'illisible sur le transport.
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

    /**
     * La table nom → classe des événements connus, pour le convertisseur de messages.
     *
     * @throws IllegalStateException si deux classes portent le même nom
     */
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

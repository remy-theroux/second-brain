package xyz.sterenn.secondbrain.knowledge.infrastructure.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import xyz.sterenn.secondbrain.knowledge.domain.DocumentAgent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AgentRefusals;

final class AgentDefinitionLoader {

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R(.*?)\\R---\\R", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z-]+)}}");
    private static final List<String> CLES_OBLIGATOIRES =
            List.of("name", "version", "refus-introuvable", "refus-hors-perimetre");

    private AgentDefinitionLoader() {}

    static Agent depuisLeClasspath(String chemin) {
        try (InputStream flux = AgentDefinitionLoader.class.getClassLoader().getResourceAsStream(chemin)) {
            if (flux == null) {
                throw new IllegalStateException(
                        "La définition d'agent " + chemin + " est introuvable dans le classpath.");
            }
            return analyse(new String(flux.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException illisible) {
            throw new IllegalStateException("La définition d'agent " + chemin + " est illisible.", illisible);
        }
    }

    static Agent analyse(String contenu) {
        Matcher enTete = FRONT_MATTER.matcher(contenu);
        if (!enTete.find()) {
            throw new IllegalStateException("La définition d'agent n'a pas de front matter délimité par ---.");
        }
        Map<String, String> cles = cles(enTete.group(1));
        for (String obligatoire : CLES_OBLIGATOIRES) {
            if (!cles.containsKey(obligatoire)) {
                throw new IllegalStateException(
                        "La définition d'agent n'a pas de clé « " + obligatoire + " » dans son front matter.");
            }
        }
        String prose = substitue(contenu.substring(enTete.end()).strip(), cles);
        if (prose.isEmpty()) {
            throw new IllegalStateException(
                    "La définition d'agent n'a pas de prose : un agent sans consignes" + " répondrait n'importe quoi.");
        }
        return new Agent(
                cles.get("name"),
                cles.get("version"),
                prose,
                new AgentRefusals(cles.get("refus-introuvable"), cles.get("refus-hors-perimetre")),
                DocumentAgent.OUTILS,
                DocumentAgent.BUDGET,
                DocumentAgent.TEMPERATURE);
    }

    private static Map<String, String> cles(String frontMatter) {
        Map<String, String> cles = new LinkedHashMap<>();
        for (String ligne : frontMatter.lines().toList()) {
            int separateur = ligne.indexOf(':');
            if (separateur > 0) {
                cles.put(
                        ligne.substring(0, separateur).strip(),
                        ligne.substring(separateur + 1).strip());
            }
        }
        return cles;
    }

    private static String substitue(String prose, Map<String, String> cles) {
        Matcher trouve = PLACEHOLDER.matcher(prose);
        StringBuilder resolu = new StringBuilder();
        while (trouve.find()) {
            String cle = trouve.group(1);
            String valeur = cles.get(cle);
            if (valeur == null) {
                throw new IllegalStateException(
                        "La définition d'agent appelle « " + cle + " », que son front matter ne déclare pas.");
            }
            trouve.appendReplacement(resolu, Matcher.quoteReplacement(valeur));
        }
        trouve.appendTail(resolu);
        return resolu.toString();
    }
}

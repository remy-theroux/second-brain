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
    private static final List<String> REQUIRED_KEYS =
            List.of("name", "version", "refus-introuvable", "refus-hors-perimetre");

    private AgentDefinitionLoader() {}

    static Agent fromClasspath(String path) {
        try (InputStream stream = AgentDefinitionLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Agent definition " + path + " was not found on the classpath.");
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new IllegalStateException("Agent definition " + path + " is unreadable.", unreadable);
        }
    }

    static Agent parse(String content) {
        Matcher header = FRONT_MATTER.matcher(content);
        if (!header.find()) {
            throw new IllegalStateException("The agent definition has no front matter delimited by ---.");
        }
        Map<String, String> keys = keys(header.group(1));
        for (String required : REQUIRED_KEYS) {
            if (!keys.containsKey(required)) {
                throw new IllegalStateException(
                        "The agent definition has no '" + required + "' key in its front matter.");
            }
        }
        String prose = substitute(content.substring(header.end()).strip(), keys);
        if (prose.isEmpty()) {
            throw new IllegalStateException(
                    "The agent definition has no prose: an agent without instructions would answer anything.");
        }
        return new Agent(
                keys.get("name"),
                keys.get("version"),
                prose,
                new AgentRefusals(keys.get("refus-introuvable"), keys.get("refus-hors-perimetre")),
                DocumentAgent.TOOLS,
                DocumentAgent.BUDGET,
                DocumentAgent.TEMPERATURE);
    }

    private static Map<String, String> keys(String frontMatter) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (String line : frontMatter.lines().toList()) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                keys.put(
                        line.substring(0, separator).strip(),
                        line.substring(separator + 1).strip());
            }
        }
        return keys;
    }

    private static String substitute(String prose, Map<String, String> keys) {
        Matcher matcher = PLACEHOLDER.matcher(prose);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = keys.get(key);
            if (value == null) {
                throw new IllegalStateException(
                        "The agent definition calls '" + key + "', which its front matter does not declare.");
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}

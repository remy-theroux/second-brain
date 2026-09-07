package xyz.sterenn.secondbrain.knowledge.domain;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Absorption;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;

public final class PromptBuilder {

    private PromptBuilder() {}

    public static LlmMessage systemMessage(Agent agent) {
        return LlmMessage.system(agent.systemPrompt());
    }

    public static String searchResult(Absorption absorption) {
        if (absorption.isEmpty()) {
            return "Aucun extrait ne correspond à cette recherche.";
        }
        if (absorption.newSources().isEmpty()) {
            return "Aucun nouvel extrait (" + absorption.alreadySeen() + " déjà vu" + plural(absorption.alreadySeen())
                    + ").";
        }
        return header(absorption) + "\n\n" + blocks(absorption.newSources());
    }

    private static String header(Absorption absorption) {
        int newSources = absorption.newSources().size();
        if (absorption.alreadySeen() == 0) {
            return newSources + " extrait" + plural(newSources) + " trouvé" + plural(newSources) + ".";
        }
        return newSources + " nouve" + (newSources > 1 ? "aux" : "l") + " extrait" + plural(newSources) + " ("
                + absorption.alreadySeen() + " déjà vu" + plural(absorption.alreadySeen()) + ").";
    }

    private static String plural(int count) {
        return count > 1 ? "s" : "";
    }

    private static String blocks(List<Source> sources) {
        StringBuilder rendered = new StringBuilder();
        for (Source source : sources) {
            if (!rendered.isEmpty()) {
                rendered.append("\n\n");
            }
            rendered.append(CitationPolicy.OPENING_MARKER)
                    .append(" numero=\"")
                    .append(source.number())
                    .append("\" document=\"")
                    .append(escape(source.filename()))
                    .append('"');
            if (!source.heading().isBlank()) {
                rendered.append(" section=\"").append(escape(source.heading())).append('"');
            }
            rendered.append(">\n").append(source.text()).append('\n').append(CitationPolicy.CLOSING_MARKER);
        }
        return rendered.toString();
    }

    /** A document name is content: without escaping, it could forge a tag. */
    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

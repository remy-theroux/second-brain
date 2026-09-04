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
        if (absorption.nouveaux().isEmpty()) {
            return "Aucun nouvel extrait (" + absorption.dejaVus() + " déjà vu" + pluriel(absorption.dejaVus()) + ").";
        }
        return entete(absorption) + "\n\n" + blocs(absorption.nouveaux());
    }

    private static String entete(Absorption absorption) {
        int nouveaux = absorption.nouveaux().size();
        if (absorption.dejaVus() == 0) {
            return nouveaux + " extrait" + pluriel(nouveaux) + " trouvé" + pluriel(nouveaux) + ".";
        }
        return nouveaux + " nouve" + (nouveaux > 1 ? "aux" : "l") + " extrait" + pluriel(nouveaux) + " ("
                + absorption.dejaVus() + " déjà vu" + pluriel(absorption.dejaVus()) + ").";
    }

    private static String pluriel(int nombre) {
        return nombre > 1 ? "s" : "";
    }

    private static String blocs(List<Source> sources) {
        StringBuilder rendu = new StringBuilder();
        for (Source source : sources) {
            if (!rendu.isEmpty()) {
                rendu.append("\n\n");
            }
            rendu.append(CitationPolicy.BALISE_OUVRANTE)
                    .append(" numero=\"")
                    .append(source.number())
                    .append("\" document=\"")
                    .append(echappe(source.filename()))
                    .append('"');
            if (!source.heading().isBlank()) {
                rendu.append(" section=\"").append(echappe(source.heading())).append('"');
            }
            rendu.append(">\n").append(source.text()).append('\n').append(CitationPolicy.BALISE_FERMANTE);
        }
        return rendu.toString();
    }

    /** Un nom de document est du contenu : sans échappement, il pourrait forger une balise. */
    private static String echappe(String valeur) {
        return valeur.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

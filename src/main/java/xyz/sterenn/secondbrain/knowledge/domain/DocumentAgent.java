package xyz.sterenn.secondbrain.knowledge.domain;

import java.time.Duration;
import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExecutionBudget;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolParameter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

/**
 * La moitié Java de l'unique agent : ce qui se raisonne. Sa prose se rédige, et vit dans
 * {@code src/main/resources/agents/document-agent.md}.
 */
public final class DocumentAgent {

    public static final String OUTIL_RECHERCHE = "rechercher_dans_les_documents";
    public static final String PARAMETRE_QUESTION = "question";

    public static final List<ToolSpecification> OUTILS = List.of(new ToolSpecification(
            OUTIL_RECHERCHE,
            "Recherche dans les documents déposés par la personne les passages les plus proches"
                    + " d'une question. Rend au plus " + SearchPolicy.RESULTS + " extraits numérotés.",
            List.of(new ToolParameter(
                    PARAMETRE_QUESTION,
                    "La question ou la formulation à rechercher, en langage naturel et en français.",
                    true))));

    /** Quatre tours, donc jusqu'à trois recherches ; la première borne atteinte gagne. */
    public static final ExecutionBudget BUDGET = new ExecutionBudget(4, Duration.ofSeconds(120));

    /** Basse : sur un petit modèle, c'est le levier le plus efficace contre l'invention. */
    public static final double TEMPERATURE = 0.2;

    private DocumentAgent() {}
}

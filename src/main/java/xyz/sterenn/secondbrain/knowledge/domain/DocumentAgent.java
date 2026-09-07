package xyz.sterenn.secondbrain.knowledge.domain;

import java.time.Duration;
import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExecutionBudget;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolParameter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

/**
 * The Java half of the single agent: what is reasoned about. Its prose is written, and lives in
 * {@code src/main/resources/agents/document-agent.md}.
 */
public final class DocumentAgent {

    public static final String SEARCH_TOOL = "rechercher_dans_les_documents";
    public static final String QUESTION_PARAMETER = "question";

    public static final List<ToolSpecification> TOOLS = List.of(new ToolSpecification(
            SEARCH_TOOL,
            "Recherche dans les documents déposés par la personne les passages les plus proches"
                    + " d'une question. Rend au plus " + SearchPolicy.RESULTS + " extraits numérotés.",
            List.of(new ToolParameter(
                    QUESTION_PARAMETER,
                    "La question ou la formulation à rechercher, en langage naturel et en français.",
                    true))));

    /** Four turns, so up to three searches; the first bound reached wins. */
    public static final ExecutionBudget BUDGET = new ExecutionBudget(4, Duration.ofSeconds(120));

    /** Low: on a small model, this is the most effective lever against fabrication. */
    public static final double TEMPERATURE = 0.2;

    private DocumentAgent() {}
}

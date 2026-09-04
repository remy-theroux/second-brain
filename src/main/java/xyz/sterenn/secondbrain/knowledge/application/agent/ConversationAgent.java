package xyz.sterenn.secondbrain.knowledge.application.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.DocumentAgent;
import xyz.sterenn.secondbrain.knowledge.domain.GroundingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.PromptBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.knowledge.domain.port.LlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Absorption;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Answer;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Question;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolCall;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

/**
 * Ni commande ni query, et le contrôleur l'appelle directement : une conversation dure des
 * minutes, et la transaction qu'ouvre un bus tiendrait une connexion PostgreSQL tout ce
 * temps. Les accès à la base restent derrière les bus — une transaction courte par recherche.
 */
@Component
public class ConversationAgent {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationAgent.class);

    private record ResultatDOutil(String texte, SourceCatalogue catalogue, Optional<String> requete) {}

    private final Agent agent;
    private final LlmPort llmPort;
    private final DocumentSearchTool documentSearchTool;
    private final Clock clock;

    public ConversationAgent(Agent agent, LlmPort llmPort, DocumentSearchTool documentSearchTool, Clock clock) {
        this.agent = agent;
        this.llmPort = llmPort;
        this.documentSearchTool = documentSearchTool;
        this.clock = clock;
    }

    public Question valide(String question) {
        return new Question(question);
    }

    public String nomDeLAgent() {
        return agent.name();
    }

    public String versionDeLAgent() {
        return agent.version();
    }

    /** Au retour, le texte de l'{@code Answer} rendue a déjà été émis par {@code onToken} : ne pas le réémettre. */
    public ConversationOutcome answer(Question question, UUID ownerId, Consumer<String> onToken) {
        Instant debut = clock.instant();
        List<LlmMessage> messages =
                new ArrayList<>(List.of(PromptBuilder.messageSysteme(agent), LlmMessage.user(question.value())));
        SourceCatalogue catalogue = SourceCatalogue.empty();
        List<String> recherches = new ArrayList<>();
        int tours = 0;

        while (tours < agent.budget().maxTurns() && !budgetEcoule(debut)) {
            tours++;
            boolean rechercheEffectuee = !recherches.isEmpty();
            CitationBuffer tampon = new CitationBuffer(catalogue, onToken);
            LlmTurn tour =
                    llmPort.stream(new LlmRequest(messages, agent.tools(), agent.temperature()), tampon::accepte);

            if (!tour.demandeUnOutil()) {
                if (tampon.texte().isBlank()) {
                    LOG.warn("Le modèle a rendu un tour vide (tour {}) ; tour consommé sans relance.", tours);
                    continue;
                }
                Answer reponse = GroundingPolicy.verdict(agent, tampon.texte(), catalogue, rechercheEffectuee);
                if (!tampon.aOuvert()) {
                    onToken.accept(reponse.text());
                }
                return new ConversationOutcome(reponse, recherches, tours, ecoule(debut));
            }

            messages.add(LlmMessage.toolRequest(tour.toolCalls()));
            for (ToolCall appel : tour.toolCalls()) {
                ResultatDOutil resultat = execute(appel, ownerId, catalogue);
                catalogue = resultat.catalogue();
                resultat.requete().ifPresent(recherches::add);
                messages.add(LlmMessage.toolResult(appel.id(), resultat.texte()));
            }
        }

        Answer reponse = GroundingPolicy.budgetDepasse(agent);
        onToken.accept(reponse.text());
        return new ConversationOutcome(reponse, recherches, tours, ecoule(debut));
    }

    private ResultatDOutil execute(ToolCall appel, UUID ownerId, SourceCatalogue catalogue) {
        if (!agent.connait(appel.name())) {
            String connus = agent.tools().stream().map(ToolSpecification::name).collect(Collectors.joining(", "));
            return new ResultatDOutil(
                    "L'outil « " + appel.name() + " » n'existe pas. Outils disponibles : " + connus + ".",
                    catalogue,
                    Optional.empty());
        }
        String requete = appel.argument(DocumentAgent.PARAMETRE_QUESTION);
        if (requete == null || requete.isBlank()) {
            return new ResultatDOutil(
                    "L'appel est incomplet : le paramètre « " + DocumentAgent.PARAMETRE_QUESTION
                            + " » est obligatoire.",
                    catalogue,
                    Optional.empty());
        }
        List<SourceCandidate> candidats;
        try {
            candidats = documentSearchTool.rechercher(requete, ownerId);
        } catch (InvalidQuestionException refus) {
            return new ResultatDOutil(
                    "La recherche a été refusée : " + refus.getMessage(), catalogue, Optional.empty());
        }
        Absorption absorption = catalogue.absorbe(candidats);
        return new ResultatDOutil(
                PromptBuilder.resultatDeRecherche(absorption), absorption.catalogue(), Optional.of(requete));
    }

    private boolean budgetEcoule(Instant debut) {
        return ecoule(debut).compareTo(agent.budget().limit()) >= 0;
    }

    private Duration ecoule(Instant debut) {
        return Duration.between(debut, clock.instant());
    }
}

package xyz.sterenn.secondbrain.knowledge.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.application.query.ChunkMatchView;
import xyz.sterenn.secondbrain.knowledge.application.query.SearchChunks;
import xyz.sterenn.secondbrain.knowledge.domain.AgentDeTest;
import xyz.sterenn.secondbrain.knowledge.domain.DocumentAgent;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.knowledge.domain.port.LlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Question;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolCall;
import xyz.sterenn.secondbrain.shared.bus.Query;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;

class ConversationAgentTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID RAPPORT = UUID.randomUUID();

    private final HorlogeDeTest horloge = new HorlogeDeTest();
    private final LlmPortScripte llmPort = new LlmPortScripte();
    private final List<String> sortis = new ArrayList<>();

    private ConversationAgent agentAvec(List<ChunkMatchView> resultatsDeRecherche) {
        QueryBus queryBus = new QueryBus() {
            @Override
            @SuppressWarnings("unchecked")
            public <R> R ask(Query<R> query) {
                assertThat(query).isInstanceOf(SearchChunks.class);
                return (R) resultatsDeRecherche;
            }
        };
        return new ConversationAgent(AgentDeTest.unAgent(), llmPort, new DocumentSearchTool(queryBus), horloge);
    }

    private static ChunkMatchView unExtrait(int position) {
        return new ChunkMatchView(RAPPORT, "rapport.pdf", position, "Rétractation", "Quatorze jours.", 0.9);
    }

    private static Question laQuestion() {
        return new Question("Quel est le délai de rétractation ?");
    }

    @Test
    void repond_sans_chercher_a_une_salutation() {
        llmPort.texte("Bonjour", ", je vous écoute.");

        ConversationOutcome resultat = agentAvec(List.of()).answer(laQuestion(), ALICE, sortis::add);

        assertThat(resultat.answer().verdict()).isEqualTo(AnswerVerdict.CONVERSATIONNELLE);
        assertThat(sortis).containsExactly("Bonjour", ", je vous écoute.");
        assertThat(resultat.recherches()).isEmpty();
        assertThat(resultat.tours()).isEqualTo(1);
    }

    @Test
    void cherche_puis_repond_en_citant_ses_sources() {
        llmPort.appelleOutil(DocumentAgent.OUTIL_RECHERCHE, "délai de rétractation")
                .texte("Quatorze jours ", "[1].");

        ConversationOutcome resultat = agentAvec(List.of(unExtrait(0))).answer(laQuestion(), ALICE, sortis::add);

        assertThat(resultat.answer().verdict()).isEqualTo(AnswerVerdict.SOURCEE);
        assertThat(resultat.answer().sources()).extracting(Source::number).containsExactly(1);
        assertThat(String.join("", sortis)).isEqualTo("Quatorze jours [1].");
        assertThat(resultat.recherches()).containsExactly("délai de rétractation");
        assertThat(resultat.tours()).isEqualTo(2);
    }

    @Test
    void n_affiche_jamais_une_reponse_qui_a_cherche_sans_rien_citer() {
        llmPort.appelleOutil(DocumentAgent.OUTIL_RECHERCHE, "capitale")
                .texte("Canberra est ", "la capitale de l'Australie.");

        ConversationOutcome resultat = agentAvec(List.of(unExtrait(0))).answer(laQuestion(), ALICE, sortis::add);

        assertThat(resultat.answer().verdict()).isEqualTo(AnswerVerdict.SANS_SOURCE);
        assertThat(sortis).containsExactly(AgentDeTest.AVEU);
        assertThat(String.join("", sortis)).doesNotContain("Canberra");
    }

    @Test
    void rend_au_modele_une_erreur_quand_il_invente_un_nom_d_outil() {
        llmPort.appelleOutil("chercher_sur_internet", "capitale").texte("Pardon.");

        agentAvec(List.of()).answer(laQuestion(), ALICE, sortis::add);

        assertThat(llmPort.dernierResultatDOutil())
                .contains("chercher_sur_internet")
                .contains("n'existe pas")
                .contains(DocumentAgent.OUTIL_RECHERCHE);
    }

    @Test
    void rend_au_modele_une_erreur_quand_l_argument_obligatoire_manque() {
        llmPort.appelleOutilSansArgument(DocumentAgent.OUTIL_RECHERCHE).texte("Pardon.");

        agentAvec(List.of()).answer(laQuestion(), ALICE, sortis::add);

        assertThat(llmPort.dernierResultatDOutil()).contains(DocumentAgent.PARAMETRE_QUESTION);
    }

    @Test
    void ne_compte_pas_comme_recherche_un_appel_d_outil_refuse() {
        llmPort.appelleOutil("chercher_sur_internet", "capitale").texte("Canberra.");

        ConversationOutcome resultat = agentAvec(List.of()).answer(laQuestion(), ALICE, sortis::add);

        assertThat(resultat.recherches()).isEmpty();
        assertThat(resultat.answer().verdict()).isEqualTo(AnswerVerdict.CONVERSATIONNELLE);
    }

    @Test
    void n_apprend_rien_d_une_recherche_repetee_et_finit_par_epuiser_ses_tours() {
        llmPort.appelleOutil(DocumentAgent.OUTIL_RECHERCHE, "délai")
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, "délai")
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, "délai")
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, "délai");

        ConversationOutcome resultat = agentAvec(List.of(unExtrait(0))).answer(laQuestion(), ALICE, sortis::add);

        assertThat(resultat.answer().verdict()).isEqualTo(AnswerVerdict.BUDGET_DEPASSE);
        assertThat(resultat.tours()).isEqualTo(4);
        assertThat(sortis).containsExactly(AgentDeTest.AVEU);
    }

    @Test
    void abandonne_quand_le_budget_de_temps_est_ecoule() {
        llmPort.aChaqueTour(() -> horloge.avance(Duration.ofSeconds(130)))
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, "délai")
                .texte("Quatorze jours [1].");

        ConversationOutcome resultat = agentAvec(List.of(unExtrait(0))).answer(laQuestion(), ALICE, sortis::add);

        assertThat(resultat.answer().verdict()).isEqualTo(AnswerVerdict.BUDGET_DEPASSE);
        assertThat(resultat.tours()).isEqualTo(1);
        assertThat(resultat.recherches()).containsExactly("délai");
    }

    @Test
    void laisse_remonter_la_deconnexion_du_client() {
        llmPort.texte("Bonjour");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> agentAvec(List.of()).answer(laQuestion(), ALICE, fragment -> {
                    throw new IllegalStateException("le client a fermé");
                }))
                .withMessageContaining("le client a fermé");
    }

    @Test
    void refuse_une_question_vide_avant_d_appeler_le_modele() {
        assertThatExceptionOfType(InvalidQuestionException.class)
                .isThrownBy(() -> agentAvec(List.of()).valide("   "));
    }

    private static final class HorlogeDeTest extends Clock {

        private Instant maintenant = Instant.parse("2026-09-04T10:00:00Z");

        void avance(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public Instant instant() {
            return maintenant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class LlmPortScripte implements LlmPort {

        private record TourScripte(List<String> fragments, List<ToolCall> appels) {}

        private final Deque<TourScripte> script = new ArrayDeque<>();
        private final List<LlmRequest> recues = new ArrayList<>();

        private Runnable aChaqueTour = () -> {};

        LlmPortScripte aChaqueTour(Runnable effet) {
            this.aChaqueTour = effet;
            return this;
        }

        LlmPortScripte texte(String... fragments) {
            script.add(new TourScripte(List.of(fragments), List.of()));
            return this;
        }

        LlmPortScripte appelleOutil(String nom, String question) {
            return ajouteUnAppel(nom, Map.of(DocumentAgent.PARAMETRE_QUESTION, question));
        }

        LlmPortScripte appelleOutilSansArgument(String nom) {
            return ajouteUnAppel(nom, Map.of());
        }

        private LlmPortScripte ajouteUnAppel(String nom, Map<String, String> arguments) {
            script.add(new TourScripte(List.of(), List.of(new ToolCall("appel-" + script.size(), nom, arguments))));
            return this;
        }

        /** Le contenu du dernier message d'outil que la boucle a rendu au modèle. */
        String dernierResultatDOutil() {
            return recues.getLast().messages().reversed().stream()
                    .filter(message -> message.role() == LlmMessage.Role.TOOL_RESULT)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Aucun résultat d'outil n'a été rendu au modèle"))
                    .content();
        }

        @Override
        public LlmTurn stream(LlmRequest request, Consumer<String> onToken) {
            recues.add(request);
            aChaqueTour.run();
            TourScripte tour =
                    script.isEmpty() ? new TourScripte(List.of("Rien à ajouter."), List.of()) : script.poll();
            StringBuilder texte = new StringBuilder();
            for (String fragment : tour.fragments()) {
                texte.append(fragment);
                onToken.accept(fragment);
            }
            return new LlmTurn(texte.toString(), tour.appels());
        }
    }
}

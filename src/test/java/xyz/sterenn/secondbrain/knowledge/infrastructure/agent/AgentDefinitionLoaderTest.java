package xyz.sterenn.secondbrain.knowledge.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.CitationPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.DocumentAgent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;

class AgentDefinitionLoaderTest {

    private static final String MINIMAL = """
            ---
            name: agent-de-test
            version: v7
            refus-introuvable: Rien trouvé.
            refus-hors-perimetre: Hors sujet.
            ---

            Tu es un agent. Quand tu ne trouves rien : « {{refus-introuvable}} ».
            Quand c'est hors sujet : « {{refus-hors-perimetre}} ».
            """;

    @Test
    void lit_le_nom_la_version_et_les_deux_refus() {
        Agent agent = AgentDefinitionLoader.analyse(MINIMAL);

        assertThat(agent.name()).isEqualTo("agent-de-test");
        assertThat(agent.version()).isEqualTo("v7");
        assertThat(agent.refusals().introuvable()).isEqualTo("Rien trouvé.");
        assertThat(agent.refusals().horsPerimetre()).isEqualTo("Hors sujet.");
    }

    @Test
    void substitue_les_messages_de_refus_dans_la_prose() {
        Agent agent = AgentDefinitionLoader.analyse(MINIMAL);

        assertThat(agent.systemPrompt()).contains("« Rien trouvé. »").doesNotContain("{{");
    }

    @Test
    void refuse_un_placeholder_qu_il_ne_sait_pas_resoudre() {
        String inconnu = MINIMAL.replace("{{refus-introuvable}}", "{{refus-inexistant}}");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.analyse(inconnu))
                .withMessageContaining("refus-inexistant");
    }

    @Test
    void refuse_un_front_matter_incomplet() {
        String sansVersion = MINIMAL.replace("version: v7\n", "");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.analyse(sansVersion))
                .withMessageContaining("version");
    }

    @Test
    void refuse_un_contenu_sans_front_matter() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.analyse("Tu es un agent."));
    }

    @Test
    void refuse_une_prose_vide() {
        String sansCorps = MINIMAL.substring(0, MINIMAL.indexOf("Tu es un agent"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.analyse(sansCorps));
    }

    @Test
    void refuse_un_fichier_absent() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.depuisLeClasspath("agents/inexistant.md"))
                .withMessageContaining("agents/inexistant.md");
    }

    @Test
    void charge_l_agent_reel_du_projet_sans_placeholder_residuel() {
        Agent agent = AgentDefinitionLoader.depuisLeClasspath("agents/document-agent.md");

        assertThat(agent.name()).isEqualTo("document-agent");
        assertThat(agent.systemPrompt()).doesNotContain("{{");
    }

    @Test
    void decrit_a_l_agent_la_balise_que_le_code_emet_reellement() {
        Agent agent = AgentDefinitionLoader.depuisLeClasspath("agents/document-agent.md");

        assertThat(agent.systemPrompt()).contains(CitationPolicy.BALISE_OUVRANTE);
        assertThat(agent.systemPrompt()).contains(CitationPolicy.BALISE_FERMANTE);
    }

    @Test
    void nomme_a_l_agent_l_outil_que_le_code_lui_declare() {
        Agent agent = AgentDefinitionLoader.depuisLeClasspath("agents/document-agent.md");

        assertThat(agent.systemPrompt()).contains(DocumentAgent.OUTIL_RECHERCHE);
    }
}

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
    void reads_the_name_the_version_and_the_two_refusals() {
        Agent agent = AgentDefinitionLoader.parse(MINIMAL);

        assertThat(agent.name()).isEqualTo("agent-de-test");
        assertThat(agent.version()).isEqualTo("v7");
        assertThat(agent.refusals().notFound()).isEqualTo("Rien trouvé.");
        assertThat(agent.refusals().outOfScope()).isEqualTo("Hors sujet.");
    }

    @Test
    void substitutes_the_refusal_messages_into_the_prose() {
        Agent agent = AgentDefinitionLoader.parse(MINIMAL);

        assertThat(agent.systemPrompt()).contains("« Rien trouvé. »").doesNotContain("{{");
    }

    @Test
    void rejects_a_placeholder_it_cannot_resolve() {
        String unknown = MINIMAL.replace("{{refus-introuvable}}", "{{refus-inexistant}}");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.parse(unknown))
                .withMessageContaining("refus-inexistant");
    }

    @Test
    void rejects_an_incomplete_front_matter() {
        String withoutVersion = MINIMAL.replace("version: v7\n", "");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.parse(withoutVersion))
                .withMessageContaining("version");
    }

    @Test
    void rejects_a_content_without_a_front_matter() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.parse("Tu es un agent."));
    }

    @Test
    void rejects_an_empty_prose() {
        String withoutBody = MINIMAL.substring(0, MINIMAL.indexOf("Tu es un agent"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.parse(withoutBody));
    }

    @Test
    void rejects_a_missing_file() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AgentDefinitionLoader.fromClasspath("agents/inexistant.md"))
                .withMessageContaining("agents/inexistant.md");
    }

    @Test
    void loads_the_real_project_agent_without_a_leftover_placeholder() {
        Agent agent = AgentDefinitionLoader.fromClasspath("agents/document-agent.md");

        assertThat(agent.name()).isEqualTo("document-agent");
        assertThat(agent.systemPrompt()).doesNotContain("{{");
    }

    @Test
    void describes_to_the_agent_the_tag_the_code_actually_emits() {
        Agent agent = AgentDefinitionLoader.fromClasspath("agents/document-agent.md");

        assertThat(agent.systemPrompt()).contains(CitationPolicy.OPENING_MARKER);
        assertThat(agent.systemPrompt()).contains(CitationPolicy.CLOSING_MARKER);
    }

    @Test
    void names_to_the_agent_the_tool_the_code_declares_to_it() {
        Agent agent = AgentDefinitionLoader.fromClasspath("agents/document-agent.md");

        assertThat(agent.systemPrompt()).contains(DocumentAgent.SEARCH_TOOL);
    }
}

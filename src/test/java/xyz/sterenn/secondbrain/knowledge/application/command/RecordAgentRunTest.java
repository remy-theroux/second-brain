package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.AgentRun;
import xyz.sterenn.secondbrain.knowledge.domain.port.AgentRunRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.CitedSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RecordAgentRunTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID alice;

    @BeforeEach
    void prepares_an_account() {
        alice = userRepository
                .save(User.register(new Email("alice@exemple.fr"), "empreinte"))
                .getId();
    }

    @Test
    void keeps_the_agent_its_version_and_the_verdict() {
        commandBus.dispatch(aRun(AnswerVerdict.GROUNDED, List.of("délai"), List.of(aSource(1))));

        List<AgentRun> runs = agentRunRepository.findByOwnerId(alice);

        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().getAgentName()).isEqualTo("document-agent");
        assertThat(runs.getFirst().getAgentVersion()).isEqualTo("v1");
        assertThat(runs.getFirst().getVerdict()).isEqualTo(AnswerVerdict.GROUNDED);
        assertThat(runs.getFirst().getTurns()).isEqualTo(2);
    }

    @Test
    void keeps_the_search_queries_in_order() {
        commandBus.dispatch(
                aRun(AnswerVerdict.GROUNDED, List.of("premier essai", "second essai"), List.of(aSource(1))));

        assertThat(agentRunRepository.findByOwnerId(alice).getFirst().getSearches())
                .containsExactly("premier essai", "second essai");
    }

    @Test
    void copies_the_text_of_the_cited_sources_rather_than_referencing_them() {
        commandBus.dispatch(aRun(AnswerVerdict.GROUNDED, List.of("délai"), List.of(aSource(3))));

        List<CitedSource> sources =
                agentRunRepository.findByOwnerId(alice).getFirst().getSources();

        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().getNumber()).isEqualTo(3);
        assertThat(sources.getFirst().getFilename()).isEqualTo("rapport.pdf");
        assertThat(sources.getFirst().getText()).isEqualTo("Quatorze jours.");
    }

    @Test
    void keeps_a_run_without_any_source() {
        commandBus.dispatch(aRun(AnswerVerdict.UNGROUNDED, List.of("capitale"), List.of()));

        assertThat(agentRunRepository.findByOwnerId(alice).getFirst().getSources())
                .isEmpty();
    }

    @Test
    void partitions_the_runs_by_owner() {
        UUID bob = userRepository
                .save(User.register(new Email("bob@exemple.fr"), "empreinte"))
                .getId();
        commandBus.dispatch(aRun(AnswerVerdict.GROUNDED, List.of("délai"), List.of(aSource(1))));

        assertThat(agentRunRepository.findByOwnerId(bob)).isEmpty();
    }

    private RecordAgentRun aRun(AnswerVerdict verdict, List<String> searches, List<Source> sources) {
        return new RecordAgentRun(
                alice,
                "document-agent",
                "v1",
                "Quel est le délai de rétractation ?",
                "Quatorze jours [3].",
                verdict,
                2,
                4200L,
                searches,
                sources);
    }

    private static Source aSource(int number) {
        return new Source(number, UUID.randomUUID(), "rapport.pdf", 0, "Rétractation", "Quatorze jours.");
    }
}

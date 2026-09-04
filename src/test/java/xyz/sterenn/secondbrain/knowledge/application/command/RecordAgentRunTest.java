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
    void prepare_un_compte() {
        alice = userRepository
                .save(User.register(new Email("alice@exemple.fr"), "empreinte"))
                .getId();
    }

    @Test
    void conserve_l_agent_sa_version_et_le_verdict() {
        commandBus.dispatch(uneTrace(AnswerVerdict.SOURCEE, List.of("délai"), List.of(uneSource(1))));

        List<AgentRun> traces = agentRunRepository.findByOwnerId(alice);

        assertThat(traces).hasSize(1);
        assertThat(traces.getFirst().getAgentName()).isEqualTo("document-agent");
        assertThat(traces.getFirst().getAgentVersion()).isEqualTo("v1");
        assertThat(traces.getFirst().getVerdict()).isEqualTo(AnswerVerdict.SOURCEE);
        assertThat(traces.getFirst().getTurns()).isEqualTo(2);
    }

    @Test
    void conserve_les_requetes_de_recherche_dans_l_ordre() {
        commandBus.dispatch(
                uneTrace(AnswerVerdict.SOURCEE, List.of("premier essai", "second essai"), List.of(uneSource(1))));

        assertThat(agentRunRepository.findByOwnerId(alice).getFirst().getSearches())
                .containsExactly("premier essai", "second essai");
    }

    @Test
    void recopie_le_texte_des_sources_citees_plutot_que_de_les_designer() {
        commandBus.dispatch(uneTrace(AnswerVerdict.SOURCEE, List.of("délai"), List.of(uneSource(3))));

        List<CitedSource> sources =
                agentRunRepository.findByOwnerId(alice).getFirst().getSources();

        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().getNumber()).isEqualTo(3);
        assertThat(sources.getFirst().getFilename()).isEqualTo("rapport.pdf");
        assertThat(sources.getFirst().getText()).isEqualTo("Quatorze jours.");
    }

    @Test
    void conserve_une_trace_sans_aucune_source() {
        commandBus.dispatch(uneTrace(AnswerVerdict.SANS_SOURCE, List.of("capitale"), List.of()));

        assertThat(agentRunRepository.findByOwnerId(alice).getFirst().getSources())
                .isEmpty();
    }

    @Test
    void cloisonne_les_traces_par_proprietaire() {
        UUID bob = userRepository
                .save(User.register(new Email("bob@exemple.fr"), "empreinte"))
                .getId();
        commandBus.dispatch(uneTrace(AnswerVerdict.SOURCEE, List.of("délai"), List.of(uneSource(1))));

        assertThat(agentRunRepository.findByOwnerId(bob)).isEmpty();
    }

    private RecordAgentRun uneTrace(AnswerVerdict verdict, List<String> recherches, List<Source> sources) {
        return new RecordAgentRun(
                alice,
                "document-agent",
                "v1",
                "Quel est le délai de rétractation ?",
                "Quatorze jours [3].",
                verdict,
                2,
                4200L,
                recherches,
                sources);
    }

    private static Source uneSource(int numero) {
        return new Source(numero, UUID.randomUUID(), "rapport.pdf", 0, "Rétractation", "Quatorze jours.");
    }
}

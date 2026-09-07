package xyz.sterenn.secondbrain.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * The bean is shared by the whole Spring context and the rollback of the test transaction does not
 * clear it: call {@link RecordingEmbeddingPort#clear()} in {@code @BeforeEach} <strong>and</strong>
 * in {@code @AfterEach}, otherwise the failure flag carries over.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingEmbeddingPortConfiguration {

    @Bean
    @Primary
    public RecordingEmbeddingPort recordingEmbeddingPort() {
        return new RecordingEmbeddingPort();
    }

    public static class RecordingEmbeddingPort implements EmbeddingPort {

        private final List<String> received = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failing = new AtomicBoolean(false);
        private final AtomicReference<Embedding> forcedAnswer = new AtomicReference<>();

        @Override
        public List<Embedding> embed(List<String> texts) {
            if (failing.get()) {
                throw new EmbeddingUnavailableException(
                        "Le service de vectorisation n'a pas répondu : ce document n'a pas pu être indexé.");
            }
            List<Embedding> vectors = new ArrayList<>();
            for (String text : texts) {
                Embedding forced = forcedAnswer.get();
                vectors.add(forced != null ? forced : vectorAtRank(received.size()));
                received.add(text);
            }
            return vectors;
        }

        /** A distinct vector per rank: a pairing off by one shows up in the assertion. */
        public static Embedding vectorAtRank(int rank) {
            return KnowledgeFixture.aVector(0.01f * (rank + 1));
        }

        public List<String> receivedTexts() {
            return List.copyOf(received);
        }

        public void willFail() {
            failing.set(true);
        }

        public void willAnswer(Embedding vector) {
            forcedAnswer.set(vector);
        }

        public void clear() {
            received.clear();
            failing.set(false);
            forcedAnswer.set(null);
        }
    }
}

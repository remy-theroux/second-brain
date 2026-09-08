package xyz.sterenn.secondbrain.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;

/**
 * Every call throws {@link DocumentStorageUnavailableException}: never import this in a test
 * that needs storage to work.
 */
@TestConfiguration(proxyBeanMethods = false)
public class UnavailableDocumentStorageConfiguration {

    @Bean
    @Primary
    public DocumentStorage unavailableDocumentStorage() {
        return new UnavailableDocumentStorage();
    }

    private static final class UnavailableDocumentStorage implements DocumentStorage {

        @Override
        public void store(UUID documentId, byte[] content) {
            throw unavailable();
        }

        @Override
        public void replace(UUID documentId, byte[] content) {
            throw unavailable();
        }

        @Override
        public void delete(UUID documentId) {
            throw unavailable();
        }

        @Override
        public Optional<byte[]> read(UUID documentId) {
            throw unavailable();
        }

        private static DocumentStorageUnavailableException unavailable() {
            return new DocumentStorageUnavailableException(
                    "The original storage did not respond.", new RuntimeException("simulated outage"));
        }
    }
}

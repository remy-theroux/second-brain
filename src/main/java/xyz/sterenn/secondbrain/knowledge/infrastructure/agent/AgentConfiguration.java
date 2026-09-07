package xyz.sterenn.secondbrain.knowledge.infrastructure.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;

@Configuration(proxyBeanMethods = false)
class AgentConfiguration {

    private static final String DEFINITION = "agents/document-agent.md";

    @Bean
    Agent documentAgent() {
        return AgentDefinitionLoader.fromClasspath(DEFINITION);
    }

    /**
     * A conversation blocks on the model for minutes: one platform thread per question would be
     * one platform thread tied up.
     */
    @Bean(destroyMethod = "close")
    ExecutorService conversationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

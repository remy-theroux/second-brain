package xyz.sterenn.secondbrain.knowledge.infrastructure.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    private static final String DEFINITION = "agents/document-agent.md";

    @Bean
    public Agent documentAgent() {
        return AgentDefinitionLoader.depuisLeClasspath(DEFINITION);
    }

    /**
     * La conversation bloque des minutes sur le modèle : un thread de plateforme par question
     * serait un thread de plateforme immobilisé.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService conversationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class GoogleDriveConfiguration {

    /** The route Google calls back, appended to the public URL the browser already knows. */
    static final String CALLBACK_PATH = "/drive/callback";

    /**
     * The injected builder carries the {@code spring.http.clients} timeouts: a token endpoint
     * that accepts the TCP connection then stops answering would otherwise freeze the thread.
     */
    @Bean
    GoogleDriveAuthorizationAdapter googleDriveAuthorizationAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${secondbrain.drive.client-id}") String clientId,
            @Value("${secondbrain.drive.client-secret}") String clientSecret,
            @Value("${secondbrain.base-url}") String baseUrl) {
        return new GoogleDriveAuthorizationAdapter(
                restClientBuilder.build(), clientId, clientSecret, baseUrl + CALLBACK_PATH);
    }
}

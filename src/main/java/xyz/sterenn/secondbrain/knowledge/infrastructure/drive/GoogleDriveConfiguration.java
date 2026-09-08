package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChanges;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFiles;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;

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

    @Bean
    GoogleAccessTokens googleAccessTokens(
            RestClient.Builder restClientBuilder,
            @Value("${secondbrain.drive.client-id}") String clientId,
            @Value("${secondbrain.drive.client-secret}") String clientSecret,
            Clock clock) {
        return new CachingGoogleAccessTokens(
                new GoogleAccessTokensAdapter(restClientBuilder.build(), clientId, clientSecret, clock), clock);
    }

    @Bean
    GoogleDriveFolders googleDriveFolders(RestClient.Builder restClientBuilder) {
        return new GoogleDriveFoldersAdapter(restClientBuilder);
    }

    @Bean
    GoogleDriveFiles googleDriveFiles(RestClient.Builder restClientBuilder) {
        return new GoogleDriveFilesAdapter(restClientBuilder);
    }

    @Bean
    GoogleDriveChanges googleDriveChanges(RestClient.Builder restClientBuilder) {
        return new GoogleDriveChangesAdapter(restClientBuilder);
    }

    @Bean
    GoogleDriveChannels googleDriveChannels(RestClient.Builder restClientBuilder) {
        return new GoogleDriveChannelsAdapter(restClientBuilder);
    }
}

package messenger.messageservice.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static messenger.commonlibs.Constants.INTERNAL_KEY_HEADER;

@Configuration
public class ReactionHttpClientConfig {
    @Bean
    RestClient restClient(
            @Value("${REACTION_SERVICE_URL:http://localhost:8085}") String reactionServiceUrl,
            @Value("${INTERNAL_API_KEY:}") String internalApiKey) {
        return RestClient.builder().baseUrl(reactionServiceUrl).defaultHeader(INTERNAL_KEY_HEADER, internalApiKey).build();
    }

    @Bean
    ReactionHttpClient reactionHttpClient(RestClient restClient) {
        return HttpServiceProxyFactory
                .builder()
                .exchangeAdapter(RestClientAdapter.create(restClient))
                .build()
                .createClient(ReactionHttpClient.class);
    }
}

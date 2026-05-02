package messenger.reactionservice.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class MessageHttpClientConfig {
    @Bean
    RestClient restClient(@Value("${MESSAGE_SERVICE_URL:http://localhost:8084}") String messageServiceUrl) {
        return RestClient.builder().baseUrl(messageServiceUrl).build();
    }

    @Bean
    MessageHttpClient messageHttpClient(RestClient restClient) {
        return HttpServiceProxyFactory
                .builder()
                .exchangeAdapter(RestClientAdapter.create(restClient))
                .build()
                .createClient(MessageHttpClient.class);
    }
}

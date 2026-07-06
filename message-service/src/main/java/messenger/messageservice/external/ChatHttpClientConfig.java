package messenger.messageservice.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static messenger.commonlibs.Constants.INTERNAL_KEY_HEADER;

@Configuration
public class ChatHttpClientConfig {
    @Bean
    RestClient chatRestClient(
            @Value("${CHAT_SERVICE_URL:http://localhost:8083}") String chatServiceUrl,
            @Value("${INTERNAL_API_KEY:}") String internalApiKey) {
        return RestClient.builder().baseUrl(chatServiceUrl).defaultHeader(INTERNAL_KEY_HEADER, internalApiKey).build();
    }

    @Bean
    ChatHttpClient chatHttpClient(RestClient chatRestClient) {
        return HttpServiceProxyFactory
                .builder()
                .exchangeAdapter(RestClientAdapter.create(chatRestClient))
                .build()
                .createClient(ChatHttpClient.class);
    }
}

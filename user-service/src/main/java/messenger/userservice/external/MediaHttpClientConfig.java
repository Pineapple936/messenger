package messenger.userservice.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static messenger.commonlibs.Constants.INTERNAL_KEY_HEADER;

@Configuration
public class MediaHttpClientConfig {
    @Bean
    RestClient restClient(
            @Value("${MEDIA_SERVICE_URL:http://localhost:8086}") String mediaServiceUrl,
            @Value("${INTERNAL_API_KEY:}") String internalApiKey) {
        return RestClient.builder()
                .baseUrl(mediaServiceUrl)
                .defaultHeader(INTERNAL_KEY_HEADER, internalApiKey)
                .build();
    }

    @Bean
    MediaHttpClient mediaHttpClient(RestClient restClient) {
        return HttpServiceProxyFactory
                .builder()
                .exchangeAdapter(RestClientAdapter.create(restClient))
                .build()
                .createClient(MediaHttpClient.class);
    }
}

package messenger.userservice.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static messenger.commonlibs.Constants.INTERNAL_KEY_HEADER;

@Configuration
public class AuthHttpClientConfig {
    @Bean
    RestClient authrestClient(
            @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authServiceUrl,
            @Value("${INTERNAL_API_KEY:}") String internalApiKey) {
        return RestClient.builder().baseUrl(authServiceUrl).defaultHeader(INTERNAL_KEY_HEADER, internalApiKey).build();
    }

    @Bean
    AuthHttpClient authHttpClient(RestClient authrestClient) {
        return HttpServiceProxyFactory
                .builder()
                .exchangeAdapter(RestClientAdapter.create(authrestClient))
                .build()
                .createClient(AuthHttpClient.class);
    }
}

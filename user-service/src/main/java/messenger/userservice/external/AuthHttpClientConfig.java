package messenger.userservice.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class AuthHttpClientConfig {
    @Bean
    RestClient authrestClient(@Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authServiceUrl) {
        return RestClient.builder().baseUrl(authServiceUrl).build();
    }

    @Bean
    AuthHttpClient authHttpClient(RestClient restClient) {
        return HttpServiceProxyFactory
                .builder()
                .exchangeAdapter(RestClientAdapter.create(restClient))
                .build()
                .createClient(AuthHttpClient.class);
    }
}

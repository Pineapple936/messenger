package messenger.gatewayservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class ChatWebClientConfig {
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    WebClient chatWebClient(@Value("${CHAT_SERVICE_URL:${CHAT_SERVICE_URI:http://localhost:8083}}") String chatServiceUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(RESPONSE_TIMEOUT)
                .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(RESPONSE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(chatServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

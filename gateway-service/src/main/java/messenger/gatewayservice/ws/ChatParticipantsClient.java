package messenger.gatewayservice.ws;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Component
public class ChatParticipantsClient {
    private final WebClient chatWebClient;

    public ChatParticipantsClient(@Qualifier("chatWebClient") WebClient chatWebClient) {
        this.chatWebClient = chatWebClient;
    }

    public List<Long> getChatUsers(Long chatId) {
        ChatUsersResponse response = chatWebClient.get()
                .uri("/chat/{chatId}/users", chatId)
                .retrieve()
                .bodyToMono(ChatUsersResponse.class)
                .block(Duration.ofSeconds(5));

        if (response == null || response.userId1() == null || response.userId2() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch chat users");
        }

        return List.of(response.userId1(), response.userId2());
    }

    private record ChatUsersResponse(Long userId1, Long userId2) {
    }
}

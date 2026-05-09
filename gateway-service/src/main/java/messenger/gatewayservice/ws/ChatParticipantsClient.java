package messenger.gatewayservice.ws;

import org.springframework.core.ParameterizedTypeReference;
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
        List<ChatParticipantEntry> participants = chatWebClient.get()
                .uri("/chat/{chatId}/users", chatId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ChatParticipantEntry>>() {})
                .block(Duration.ofSeconds(5));

        if (participants == null || participants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch chat users");
        }

        return participants.stream()
                .map(ChatParticipantEntry::userId)
                .filter(id -> id != null)
                .toList();
    }

    private record ChatParticipantEntry(Long userId, String role, String customChatName) {
    }
}

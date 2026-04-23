package messenger.messageservice.external;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/chat"
)
public interface ChatHttpClient {
    @GetExchange("/{chatId}/users/{userId}/exists")
    boolean hasUser(@PathVariable("chatId") Long chatId, @PathVariable("userId") Long userId);
}

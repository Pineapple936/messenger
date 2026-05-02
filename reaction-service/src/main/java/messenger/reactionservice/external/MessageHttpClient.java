package messenger.reactionservice.external;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/message"
)
public interface MessageHttpClient {
    @GetExchange("/{messageId}/exists/{userId}")
    boolean isMessageOwner(@PathVariable Long userId,
                           @PathVariable String messageId);
}

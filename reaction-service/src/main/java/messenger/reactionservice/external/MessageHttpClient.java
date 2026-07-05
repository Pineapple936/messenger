package messenger.reactionservice.external;

import messenger.commonlibs.dto.messageservice.MessageAccessInfoDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/message"
)
public interface MessageHttpClient {
    @GetExchange("/{messageId}/exists/{userId}")
    boolean isMessageOwner(@PathVariable Long userId,
                           @PathVariable String messageId);

    @GetExchange("/{messageId}/access")
    MessageAccessInfoDto getMessageAccessInfo(@PathVariable String messageId,
                                              @RequestHeader(USER_ID_HEADER) Long userId);
}

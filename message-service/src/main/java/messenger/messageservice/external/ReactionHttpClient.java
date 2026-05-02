package messenger.messageservice.external;

import jakarta.validation.Valid;
import messenger.commonlibs.dto.messageservice.ReactionsOnMessageListRequest;
import messenger.commonlibs.dto.messageservice.ReactionsOnMessageListResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(
    accept = "application/json",
    contentType = "application/json",
    url = "/reaction"
)
public interface ReactionHttpClient {
    @PostExchange("/message/batchByUser")
    ReactionsOnMessageListResponse getReactions(@RequestBody @Valid ReactionsOnMessageListRequest req);
}

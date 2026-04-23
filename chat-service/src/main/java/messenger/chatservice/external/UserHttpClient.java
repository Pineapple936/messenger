package messenger.chatservice.external;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/user"
)
public interface UserHttpClient {
    @GetExchange("/exists/{userId}")
    Boolean existsUserByUserId(@PathVariable("userId") Long userId);
}

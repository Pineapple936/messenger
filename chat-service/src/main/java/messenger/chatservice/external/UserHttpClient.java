package messenger.chatservice.external;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/user"
)
public interface UserHttpClient {
    @PostExchange("/exists/users")
    List<Long> existsUsersById(@RequestBody List<Long> ids);

    @GetExchange("/exists/user/{userId}")
    Boolean existsUserById(@PathVariable Long userId);
}

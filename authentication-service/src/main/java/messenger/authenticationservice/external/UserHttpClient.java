package messenger.authenticationservice.external;

import messenger.commonlibs.dto.userservice.CreateUserDto;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/user"
)
public interface UserHttpClient {
    @PostExchange
    void createUser(@RequestBody CreateUserDto request);
}

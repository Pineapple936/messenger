package messenger.userservice.external;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.http.HttpHeaders;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/auth"
)
public interface AuthHttpClient {
    @DeleteExchange
    void deleteUserCredentialById(@RequestHeader(USER_ID_HEADER) Long userId,
                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader);
}

package messenger.userservice.external;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/media"
)
public interface MediaHttpClient {
    @DeleteExchange("/{filename}")
    void deleteByFileName(@PathVariable String filename);
}

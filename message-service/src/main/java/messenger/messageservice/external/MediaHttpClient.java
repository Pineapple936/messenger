package messenger.messageservice.external;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange(
        accept = "application/json",
        contentType = "application/json",
        url = "/media"
)
public interface MediaHttpClient {
    @DeleteExchange
    void deleteByListName(@RequestBody List<String> names);
}

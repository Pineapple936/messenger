package messenger.commonlibs.dto;

import java.time.LocalDateTime;

public record ErrorDto(
        String name,
        String message,
        LocalDateTime localDateTime
) {
}

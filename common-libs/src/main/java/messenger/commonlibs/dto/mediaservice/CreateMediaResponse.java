package messenger.commonlibs.dto.mediaservice;

import jakarta.validation.constraints.NotBlank;

public record CreateMediaResponse(
        @NotBlank
        String url,

        @NotBlank
        String filename
) {
}

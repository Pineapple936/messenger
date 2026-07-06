package messenger.mediaservice.api;

import messenger.mediaservice.domain.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {
    @Mock
    private MediaService mediaService;

    private MediaController controller;

    @BeforeEach
    void setUp() {
        controller = new MediaController(mediaService);
    }

    @Test
    void uploadRejectsEmptyFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[0]);

        ResponseEntity<?> response = controller.upload(file);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void downloadUsesContentTypeFromFileExtension() {
        ByteArrayResource body = new ByteArrayResource(new byte[] {1, 2, 3});
        when(mediaService.downloadFile("avatar.png")).thenReturn(body);

        ResponseEntity<ByteArrayResource> response = controller.download("avatar.png");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isSameAs(body);
    }

    @Test
    void deleteByListDelegatesAllFileNamesToService() {
        controller.deleteByList(java.util.List.of("a.jpg", "b.jpg"));

        verify(mediaService).deleteFileByList(java.util.List.of("a.jpg", "b.jpg"));
    }
}

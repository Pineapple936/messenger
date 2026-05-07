package messenger.mediaservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.mediaservice.CreateMediaResponse;
import messenger.mediaservice.domain.MediaService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaService mediaService;

    @PostMapping("/upload")
    public ResponseEntity<CreateMediaResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String fileName = UUID.randomUUID() + getExtension(file.getOriginalFilename());
        String url = mediaService.uploadFile(file, fileName);

        return ResponseEntity.ok(new CreateMediaResponse(url, fileName));
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable @NotBlank String fileName) {
        ByteArrayResource resource = mediaService.downloadFile(fileName);
        return ResponseEntity.ok()
                .contentType(imageContentType(fileName))
                .body(resource);
    }

    @GetMapping("/proxy")
    public ResponseEntity<ByteArrayResource> proxy(@RequestParam @NotBlank String publicUrl) {
        ByteArrayResource resource = mediaService.downloadPublicFile(publicUrl);
        return ResponseEntity.ok()
                .contentType(imageContentType(publicUrl))
                .body(resource);
    }

    @GetMapping("/list")
    public ResponseEntity<List<String>> list() {
        return ResponseEntity.ok(mediaService.listFiles());
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<Void> delete(@PathVariable @NotBlank String fileName) {
        mediaService.deleteFile(fileName);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteByList(@RequestBody @NotNull List<@NotBlank String> files) {
        mediaService.deleteFileByList(files);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        return ResponseEntity.status(500).body(e.getClass().getName() + ": " + e.getMessage());
    }

    private MediaType imageContentType(String name) {
        if (name == null) return MediaType.IMAGE_JPEG;
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    private String getExtension(String originalName) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf("."));
        }
        return ".jpg";
    }
}

package messenger.mediaservice.domain;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.ParameterizedTypeReference;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class MediaService {

    private final String token;
    private final RestTemplate restTemplate = new RestTemplate();

    public MediaService(@Value("${yandex.disk.token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISK_TOKEN environment variable is not set");
        }
        this.token = token;
    }
    private static final String BASE_PATH = "/messenger/";
    private static final String DISK_API = "https://cloud-api.yandex.net/v1/disk/resources";

    public String uploadFile(MultipartFile file, String fileName) throws IOException {
        ensureBaseDirExists();
        String diskPath = BASE_PATH + fileName;
        String uploadUrl = getUploadUrl(diskPath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : "image/jpeg"
        ));
        HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);
        restTemplate.exchange(uploadUrl, HttpMethod.PUT, entity, Void.class);

        return fileName;
    }

    public ByteArrayResource downloadFile(String fileName) {
        String diskPath = BASE_PATH + fileName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String url = DISK_API + "/download?path=" + diskPath;
        Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        String downloadUrl = (String) response.get("href");

        byte[] bytes = restTemplate.exchange(java.net.URI.create(downloadUrl), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class).getBody();
        return new ByteArrayResource(bytes);
    }

    public ByteArrayResource downloadPublicFile(String publicUrl) {
        String apiUrl = "https://cloud-api.yandex.net/v1/disk/public/resources/download?public_key="
                + URLEncoder.encode(publicUrl, StandardCharsets.UTF_8);
        Map<String, Object> response = restTemplate.exchange(apiUrl, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        if (response == null || response.get("href") == null) {
            throw new IllegalArgumentException("Failed to get download link for public URL");
        }
        byte[] bytes = restTemplate.exchange(java.net.URI.create((String) response.get("href")), HttpMethod.GET, null, byte[].class).getBody();
        return new ByteArrayResource(bytes != null ? bytes : new byte[0]);
    }

    public void deleteFile(String fileName) {
        String diskPath = BASE_PATH + fileName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(
                    DISK_API + "?path=" + diskPath + "&permanently=true",
                    HttpMethod.DELETE, entity, Void.class
            );
        } catch (HttpClientErrorException.NotFound ignored) {
        }
    }

    public void deleteFileByList(List<String> files) {
        for (var file : files) {
            deleteFile(file);
        }
    }

    public List<String> listFiles() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String url = DISK_API + "?path=" + BASE_PATH + "&fields=_embedded.items.name,_embedded.items.public_url";
        Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> embedded = (Map<String, Object>) response.get("_embedded");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) embedded.get("items");

        return items.stream()
                .map(item -> (String) item.get("public_url"))
                .filter(u -> u != null)
                .toList();
    }

    private void ensureBaseDirExists() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(
                    DISK_API + "?path=" + BASE_PATH.replaceAll("/$", ""),
                    HttpMethod.GET, entity, Map.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            restTemplate.exchange(
                    DISK_API + "?path=" + BASE_PATH.replaceAll("/$", ""),
                    HttpMethod.PUT, entity, Void.class
            );
        }
    }

    private String getUploadUrl(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String url = DISK_API + "/upload?path=" + path + "&overwrite=true";
        Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        return (String) response.get("href");
    }
}

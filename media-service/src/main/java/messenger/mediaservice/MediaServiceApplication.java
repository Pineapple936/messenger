package messenger.mediaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"messenger.mediaservice", "messenger.commonlibs.security"})
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }

}

package messenger.authenticationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"messenger.authenticationservice", "messenger.commonlibs.security"})
public class AuthenticationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthenticationServiceApplication.class, args);
    }

}

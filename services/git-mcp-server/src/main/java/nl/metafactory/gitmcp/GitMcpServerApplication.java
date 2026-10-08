package nl.metafactory.gitmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GitMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitMcpServerApplication.class, args);
    }
}

package desm.dps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdministrationServerApp {
    private static final Logger logger = LoggerFactory.getLogger(AdministrationServerApp.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(AdministrationServerApp.class, args);
            logger.info("Administration Server started successfully on http://localhost:8080");
        } catch (Exception e) {
            logger.error("Failed to start Administration Server", e);
            System.exit(1);
        }
    }
}
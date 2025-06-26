package desm.dps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The main entry point for the Administration Server Spring Boot application.
 */
@SpringBootApplication
public class AdministrationServerApp {
    private static final Logger logger = LoggerFactory.getLogger(AdministrationServerApp.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(AdministrationServerApp.class, args);
            logger.info("Administration Server has started successfully.");
        } catch (Exception e) {
            logger.error("Failed to start the Administration Server.", e);
            // Exit with a non-zero status code to indicate failure to external scripts/services.
            System.exit(1);
        }
    }
}
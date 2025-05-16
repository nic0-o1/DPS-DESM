package desm.dps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdministrationServerApp {
    public static void main(String[] args) {
        SpringApplication.run(AdministrationServerApp.class, args);
        System.out.println("Server running on http://localhost:8080");
    }
}

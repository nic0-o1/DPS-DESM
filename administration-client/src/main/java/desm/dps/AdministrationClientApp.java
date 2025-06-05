package desm.dps;

import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Client application for interacting with the power plant administration system.
 */
public class AdministrationClientApp {
    private final Scanner scanner = new Scanner(System.in);
    private final AdministrationService adminService;

    public AdministrationClientApp() {
        this.adminService = new AdministrationService();
    }

    public static void main(String[] args) {
        new AdministrationClientApp().run();
    }

    public void run() {
        while (true) {
            displayMenu();
            int choice = getUserChoice();
            switch (choice) {
                case 1 -> adminService.printAllPlants();
                case 2 -> adminService.printPollutionData(scanner);
                case 3 -> {
                    System.out.println("Exiting...");
                    return;
                }
                default -> System.out.println("Invalid choice. Enter 1 - 3.");
            }
        }
    }

    private void displayMenu() {
        System.out.println("\n===== CLIENT ADMINISTRATION =====");
        System.out.println("1. Get all power plants");
        System.out.println("2. Get pollution data");
        System.out.println("3. Exit");
        System.out.print("Enter your choice (1-3): ");

    }

    private int getUserChoice() {
        try {
            return scanner.nextInt();
        } catch (InputMismatchException e) {
            System.out.println("Please enter a valid number.");
            return -1;
        } finally {
            scanner.nextLine();
        }
    }
}
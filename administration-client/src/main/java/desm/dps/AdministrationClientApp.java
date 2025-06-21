package desm.dps;

import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * A command-line interface (CLI) client for interacting with the power plant administration system.
 * It provides a simple menu-driven interface for the user to execute various administration tasks.
 */
public class AdministrationClientApp {
    private final Scanner scanner = new Scanner(System.in);
    private final AdministrationService adminService;

    /**
     * Constructs the client application and initializes the administration service.
     */
    public AdministrationClientApp() {
        this.adminService = new AdministrationService();
    }

    /**
     * The main entry point for the application.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        new AdministrationClientApp().run();
    }

    /**
     * Runs the main application loop, which displays the menu,
     * gets user input, and dispatches actions until the user chooses to exit.
     */
    public void run() {
        while (true) {
            displayMenu();
            int choice = getUserChoice();

            switch (choice) {
                case 1 -> adminService.printAllPlants();
                case 2 -> adminService.printPollutionData(scanner);
                case 3 -> {
                    System.out.println("Exiting application...");
                    return;
                }
                default -> System.err.println("Invalid choice. Please enter a number between 1 and 3.");
            }
        }
    }

    /**
     * Displays the main menu of options to the user.
     */
    private void displayMenu() {
        System.out.println("\n===== CLIENT ADMINISTRATION MENU =====");
        System.out.println("1. List all power plants");
        System.out.println("2. Get CO2 pollution data for a time range");
        System.out.println("3. Exit");
        System.out.print("Enter your choice (1-3): ");
    }

    /**
     * Reads and validates the user's menu choice.
     * Handles non-integer input gracefully.
     *
     * @return The integer choice from the user, or -1 if the input was invalid.
     */
    private int getUserChoice() {
        try {
            return scanner.nextInt();
        } catch (InputMismatchException e) {
            System.err.println("Invalid input. Please enter a valid number.");
            return -1;
        } finally {
            scanner.nextLine();
        }
    }
}
import java.util.Scanner;

public class UserInput {
    private String name;
    private int age;
    private Scanner scanner;

    // Constructor 1: No-argument constructor
    public UserInput() {
        this.scanner = new Scanner(System.in);
        this.name = "";
        this.age = 0;
    }

    // Constructor 2: Constructor with parameters
    public UserInput(String name, int age) {
        this.scanner = new Scanner(System.in);
        this.name = name;
        this.age = age;
    }

    // Method to accept user input
    public void getUserInput() {
        System.out.print("Enter your name: ");
        this.name = scanner.nextLine();

        System.out.print("Enter your age: ");
        this.age = scanner.nextInt();
    }

    // Display user information
    public void displayInfo() {
        System.out.println("\n--- User Information ---");
        System.out.println("Name: " + this.name);
        System.out.println("Age: " + this.age);
    }

    // Main method to test the class
    public static void main(String[] args) {
        // Using Constructor 1
        UserInput user1 = new UserInput();
        System.out.println("Using Constructor 1 (No-argument):");
        user1.getUserInput();
        user1.displayInfo();

        // Using Constructor 2
        System.out.println("\n\nUsing Constructor 2 (With parameters):");
        UserInput user2 = new UserInput("John Doe", 25);
        user2.displayInfo();
    }
}

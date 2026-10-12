import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        new BankApplication().start();
    }

    static class BankApplication {
        private final FileStorage fileStorage = new FileStorage();
        private final Scanner scanner = new Scanner(System.in);
        private final Map<String, User> usersByUsername = new HashMap<>();
        private final Map<String, User> usersById = new HashMap<>();
        private final Map<String, List<Account>> accountsByUserId = new HashMap<>();
        private final Map<String, List<Transaction>> transactionsByUserId = new HashMap<>();
        private final Map<String, Integer> failedLoginAttempts = new HashMap<>();
        private User currentUser;

        void start() {
            loadData();
            System.out.println("========================================");
            System.out.println("      SECURE BANKING APPLICATION");
            System.out.println("========================================");

            while (true) {
                if (currentUser == null) {
                    showPublicMenu();
                } else {
                    showAuthenticatedMenu();
                }
            }
        }

        private void showPublicMenu() {
            System.out.println("\nMain Menu");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");

            int choice = readInt("Choose an option: ");
            switch (choice) {
                case 1 -> registerUser();
                case 2 -> loginUser();
                case 3 -> {
                    System.out.println("Goodbye!");
                    scanner.close();
                    System.exit(0);
                }
                default -> System.out.println("Invalid choice. Please try again.");
            }
        }

        private void showAuthenticatedMenu() {
            System.out.println("\nWelcome, " + currentUser.fullName + "!");
            System.out.println("1. Create account");
            System.out.println("2. View balance");
            System.out.println("3. Deposit");
            System.out.println("4. Withdraw");
            System.out.println("5. View transaction history");
            System.out.println("6. Logout");
            System.out.println("7. Exit");

            int choice = readInt("Choose an option: ");
            switch (choice) {
                case 1 -> createAccount();
                case 2 -> viewBalance();
                case 3 -> depositFunds();
                case 4 -> withdrawFunds();
                case 5 -> viewTransactionHistory();
                case 6 -> logout();
                case 7 -> {
                    System.out.println("Thank you for banking with us. Goodbye!");
                    scanner.close();
                    System.exit(0);
                }
                default -> System.out.println("Invalid choice. Please try again.");
            }
        }

        private void registerUser() {
            String fullName = readNonEmpty("Enter full name: ");
            String username = readNonEmpty("Choose username: ").trim();
            if (usersByUsername.containsKey(username.toLowerCase(Locale.ROOT))) {
                System.out.println("Username already exists. Please try another one.");
                return;
            }

            String password = readPassword("Create a password (min 8 chars, must include letters and numbers): ");
            if (!isValidPassword(password)) {
                System.out.println("Password must be at least 8 characters and contain both letters and numbers.");
                return;
            }

            String salt = generateSalt();
            String passwordHash = hashPassword(password, salt);
            User user = new User(
                    UUID.randomUUID().toString(),
                    fullName,
                    username,
                    passwordHash,
                    salt
            );

            usersByUsername.put(username.toLowerCase(Locale.ROOT), user);
            usersById.put(user.userId, user);
            fileStorage.saveUsers(new ArrayList<>(usersById.values()));
            System.out.println("Registration successful! Please login to continue.");
        }

        private void loginUser() {
            String username = readNonEmpty("Enter username: ").trim();
            String normalizedUsername = username.toLowerCase(Locale.ROOT);

            User user = usersByUsername.get(normalizedUsername);
            if (user == null) {
                System.out.println("User not found.");
                return;
            }

            if (failedLoginAttempts.getOrDefault(normalizedUsername, 0) >= 3) {
                System.out.println("Account locked due to too many failed attempts. Please contact support.");
                return;
            }

            String password = readPassword("Enter password: ");
            if (isValidPasswordForUser(user, password)) {
                currentUser = user;
                failedLoginAttempts.remove(normalizedUsername);
                System.out.println("Login successful.");
            } else {
                int attempts = failedLoginAttempts.getOrDefault(normalizedUsername, 0) + 1;
                failedLoginAttempts.put(normalizedUsername, attempts);
                System.out.println("Invalid username or password. Attempts left: " + (3 - attempts));
                if (attempts >= 3) {
                    System.out.println("This account is now locked for security reasons.");
                }
            }
        }

        private void createAccount() {
            System.out.println("Create a New Account");
            String accountType = readNonEmpty("Account type (Savings/Current): ").trim();
            String sanitizedType = accountType.substring(0, 1).toUpperCase() + accountType.substring(1).toLowerCase();
            if (!"Savings".equals(sanitizedType) && !"Current".equals(sanitizedType)) {
                System.out.println("Invalid account type. Please choose Savings or Current.");
                return;
            }

            String accountNumber = generateUniqueAccountNumber();
            Account account = new Account(
                    UUID.randomUUID().toString(),
                    currentUser.userId,
                    accountNumber,
                    sanitizedType,
                    BigDecimal.ZERO
            );

            accountsByUserId.computeIfAbsent(currentUser.userId, key -> new ArrayList<>()).add(account);
            fileStorage.saveAccounts(getAllAccounts());
            System.out.println("Account created successfully. Account number: " + accountNumber);
        }

        private void viewBalance() {
            List<Account> userAccounts = getCurrentUserAccounts();
            if (userAccounts.isEmpty()) {
                System.out.println("You have no accounts yet.");
                return;
            }

            for (Account account : userAccounts) {
                System.out.println(account.accountNumber + " | " + account.accountType + " | Balance: " + formatMoney(account.balance));
            }

            String accountNumber = readNonEmpty("Enter the account number to view details: ").trim();
            Account account = findAccountByNumber(accountNumber);
            if (account == null) {
                System.out.println("Account not found.");
                return;
            }

            System.out.println("Account: " + account.accountNumber);
            System.out.println("Balance: " + formatMoney(account.balance));
        }

        private void depositFunds() {
            Account account = selectAccount("Deposit funds into which account?");
            if (account == null) {
                return;
            }

            BigDecimal amount = readMoneyAmount("Enter amount to deposit: ");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("Deposit amount must be greater than zero.");
                return;
            }

            account.balance = account.balance.add(amount);
            recordTransaction(account.accountNumber, "DEPOSIT", amount, "Cash deposit");
            fileStorage.saveAccounts(getAllAccounts());
            System.out.println("Deposit successful. New balance: " + formatMoney(account.balance));
        }

        private void withdrawFunds() {
            Account account = selectAccount("Withdraw from which account?");
            if (account == null) {
                return;
            }

            BigDecimal amount = readMoneyAmount("Enter amount to withdraw: ");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("Withdrawal amount must be greater than zero.");
                return;
            }

            if (account.balance.compareTo(amount) < 0) {
                System.out.println("Insufficient funds. Available balance: " + formatMoney(account.balance));
                return;
            }

            account.balance = account.balance.subtract(amount);
            recordTransaction(account.accountNumber, "WITHDRAWAL", amount, "Cash withdrawal");
            fileStorage.saveAccounts(getAllAccounts());
            System.out.println("Withdrawal successful. Remaining balance: " + formatMoney(account.balance));
        }

        private void viewTransactionHistory() {
            List<Account> userAccounts = getCurrentUserAccounts();
            if (userAccounts.isEmpty()) {
                System.out.println("No account exists for this user.");
                return;
            }

            String accountNumber = readNonEmpty("Enter account number to view transactions: ").trim();
            Account account = findAccountByNumber(accountNumber);
            if (account == null) {
                System.out.println("Account not found.");
                return;
            }

            List<Transaction> history = transactionsByUserId.getOrDefault(currentUser.userId, new ArrayList<>());
            boolean found = false;
            for (Transaction transaction : history) {
                if (transaction.accountNumber.equals(account.accountNumber)) {
                    found = true;
                    System.out.println(transaction.timestamp + " | " + transaction.type + " | " + formatMoney(transaction.amount) + " | " + transaction.description);
                }
            }

            if (!found) {
                System.out.println("No transaction history found for this account.");
            }
        }

        private void logout() {
            currentUser = null;
            System.out.println("You have been logged out.");
        }

        private Account selectAccount(String prompt) {
            List<Account> userAccounts = getCurrentUserAccounts();
            if (userAccounts.isEmpty()) {
                System.out.println("No accounts available for this user.");
                return null;
            }

            System.out.println("Your accounts:");
            for (Account account : userAccounts) {
                System.out.println(account.accountNumber + " | " + account.accountType + " | " + formatMoney(account.balance));
            }

            String accountNumber = readNonEmpty(prompt + " ").trim();
            Account selected = findAccountByNumber(accountNumber);
            if (selected == null) {
                System.out.println("Account not found.");
                return null;
            }
            return selected;
        }

        private void loadData() {
            List<User> users = fileStorage.loadUsers();
            for (User user : users) {
                usersByUsername.put(user.username.toLowerCase(Locale.ROOT), user);
                usersById.put(user.userId, user);
            }

            List<Account> accounts = fileStorage.loadAccounts();
            for (Account account : accounts) {
                accountsByUserId.computeIfAbsent(account.userId, key -> new ArrayList<>()).add(account);
            }

            List<Transaction> transactions = fileStorage.loadTransactions();
            for (Transaction transaction : transactions) {
                transactionsByUserId.computeIfAbsent(transaction.userId, key -> new ArrayList<>()).add(transaction);
            }
        }

        private List<Account> getCurrentUserAccounts() {
            return accountsByUserId.getOrDefault(currentUser.userId, new ArrayList<>());
        }

        private List<Account> getAllAccounts() {
            List<Account> allAccounts = new ArrayList<>();
            for (List<Account> accounts : accountsByUserId.values()) {
                allAccounts.addAll(accounts);
            }
            return allAccounts;
        }

        private void recordTransaction(String accountNumber, String type, BigDecimal amount, String description) {
            Transaction transaction = new Transaction(
                    UUID.randomUUID().toString(),
                    currentUser.userId,
                    accountNumber,
                    type,
                    amount,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    description,
                    "SUCCESS"
            );
            transactionsByUserId.computeIfAbsent(currentUser.userId, key -> new ArrayList<>()).add(transaction);
            fileStorage.saveTransactions(getAllTransactions());
        }

        private List<Transaction> getAllTransactions() {
            List<Transaction> allTransactions = new ArrayList<>();
            for (List<Transaction> transactions : transactionsByUserId.values()) {
                allTransactions.addAll(transactions);
            }
            return allTransactions;
        }

        private Account findAccountByNumber(String accountNumber) {
            for (List<Account> accounts : accountsByUserId.values()) {
                for (Account account : accounts) {
                    if (account.accountNumber.equals(accountNumber)) {
                        return account;
                    }
                }
            }
            return null;
        }

        private String generateUniqueAccountNumber() {
            String accountNumber;
            do {
                accountNumber = String.format("%010d", (long) (Math.random() * 9000000000L) + 1000000000L);
            } while (accountExists(accountNumber));
            return accountNumber;
        }

        private boolean accountExists(String accountNumber) {
            for (List<Account> accounts : accountsByUserId.values()) {
                for (Account account : accounts) {
                    if (account.accountNumber.equals(accountNumber)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private String readNonEmpty(String prompt) {
            String input;
            while (true) {
                System.out.print(prompt);
                input = scanner.nextLine();
                if (input != null && !input.trim().isEmpty()) {
                    return input.trim();
                }
                System.out.println("Please enter a valid value.");
            }
        }

        private String readPassword(String prompt) {
            System.out.print(prompt);
            return scanner.nextLine();
        }

        private int readInt(String prompt) {
            while (true) {
                System.out.print(prompt);
                String input = scanner.nextLine();
                try {
                    return Integer.parseInt(input.trim());
                } catch (NumberFormatException exc) {
                    System.out.println("Please enter a valid number.");
                }
            }
        }

        private BigDecimal readMoneyAmount(String prompt) {
            while (true) {
                System.out.print(prompt);
                String input = scanner.nextLine();
                try {
                    return new BigDecimal(input.trim());
                } catch (NumberFormatException exc) {
                    System.out.println("Please enter a valid amount.");
                }
            }
        }

        private boolean isValidPassword(String password) {
            if (password == null || password.length() < 8) {
                return false;
            }
            boolean hasLetter = false;
            boolean hasDigit = false;
            for (char c : password.toCharArray()) {
                if (Character.isLetter(c)) {
                    hasLetter = true;
                }
                if (Character.isDigit(c)) {
                    hasDigit = true;
                }
            }
            return hasLetter && hasDigit;
        }

        private boolean isValidPasswordForUser(User user, String password) {
            return Objects.equals(hashPassword(password, user.salt), user.passwordHash);
        }

        private String hashPassword(String password, String salt) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String salted = salt + password;
                byte[] hash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hash);
            } catch (NoSuchAlgorithmException exc) {
                throw new IllegalStateException("SHA-256 is not available", exc);
            }
        }

        private String generateSalt() {
            SecureRandom random = new SecureRandom();
            byte[] saltBytes = new byte[16];
            random.nextBytes(saltBytes);
            return Base64.getEncoder().encodeToString(saltBytes);
        }

        private String formatMoney(BigDecimal value) {
            return "KSh " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        }
    }

    static class User {
        String userId;
        String fullName;
        String username;
        String passwordHash;
        String salt;

        User(String userId, String fullName, String username, String passwordHash, String salt) {
            this.userId = userId;
            this.fullName = fullName;
            this.username = username;
            this.passwordHash = passwordHash;
            this.salt = salt;
        }

        String toFileString() {
            return userId + "|" + fullName + "|" + username + "|" + passwordHash + "|" + salt;
        }

        static User fromFileString(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 5) {
                return null;
            }
            return new User(parts[0], parts[1], parts[2], parts[3], parts[4]);
        }
    }

    static class Account {
        String accountId;
        String userId;
        String accountNumber;
        String accountType;
        BigDecimal balance;

        Account(String accountId, String userId, String accountNumber, String accountType, BigDecimal balance) {
            this.accountId = accountId;
            this.userId = userId;
            this.accountNumber = accountNumber;
            this.accountType = accountType;
            this.balance = balance;
        }

        String toFileString() {
            return accountId + "|" + userId + "|" + accountNumber + "|" + accountType + "|" + balance.toPlainString();
        }

        static Account fromFileString(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 5) {
                return null;
            }
            return new Account(parts[0], parts[1], parts[2], parts[3], new BigDecimal(parts[4]));
        }
    }

    static class Transaction {
        String transactionId;
        String userId;
        String accountNumber;
        String type;
        BigDecimal amount;
        String timestamp;
        String description;
        String status;

        Transaction(String transactionId, String userId, String accountNumber, String type, BigDecimal amount,
                    String timestamp, String description, String status) {
            this.transactionId = transactionId;
            this.userId = userId;
            this.accountNumber = accountNumber;
            this.type = type;
            this.amount = amount;
            this.timestamp = timestamp;
            this.description = description;
            this.status = status;
        }

        String toFileString() {
            return transactionId + "|" + userId + "|" + accountNumber + "|" + type + "|" + amount.toPlainString() + "|" + timestamp + "|" + description + "|" + status;
        }

        static Transaction fromFileString(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 8) {
                return null;
            }
            return new Transaction(parts[0], parts[1], parts[2], parts[3], new BigDecimal(parts[4]), parts[5], parts[6], parts[7]);
        }
    }

    static class FileStorage {
        private static final Path USERS_FILE = Paths.get("users.txt");
        private static final Path ACCOUNTS_FILE = Paths.get("accounts.txt");
        private static final Path TRANSACTIONS_FILE = Paths.get("transactions.txt");

        List<User> loadUsers() {
            return readUsersFromFile(USERS_FILE);
        }

        List<Account> loadAccounts() {
            return readAccountsFromFile(ACCOUNTS_FILE);
        }

        List<Transaction> loadTransactions() {
            return readTransactionsFromFile(TRANSACTIONS_FILE);
        }

        void saveUsers(List<User> users) {
            try {
                ensureFileExists(USERS_FILE);
                List<String> lines = new ArrayList<>();
                for (User user : users) {
                    lines.add(user.toFileString());
                }
                Files.write(USERS_FILE, lines, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Error saving user data: " + e.getMessage());
            }
        }

        void saveAccounts(List<Account> accounts) {
            try {
                ensureFileExists(ACCOUNTS_FILE);
                List<String> lines = new ArrayList<>();
                for (Account account : accounts) {
                    lines.add(account.toFileString());
                }
                Files.write(ACCOUNTS_FILE, lines, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Error saving account data: " + e.getMessage());
            }
        }

        void saveTransactions(List<Transaction> transactions) {
            try {
                ensureFileExists(TRANSACTIONS_FILE);
                List<String> lines = new ArrayList<>();
                for (Transaction transaction : transactions) {
                    lines.add(transaction.toFileString());
                }
                Files.write(TRANSACTIONS_FILE, lines, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Error saving transaction data: " + e.getMessage());
            }
        }

        private void ensureFileExists(Path path) throws IOException {
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        }

        private List<User> readUsersFromFile(Path path) {
            List<User> users = new ArrayList<>();
            try {
                if (!Files.exists(path)) {
                    Files.createFile(path);
                    return users;
                }
                List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }
                    User user = User.fromFileString(line);
                    if (user != null) {
                        users.add(user);
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading user data: " + e.getMessage());
            }
            return users;
        }

        private List<Account> readAccountsFromFile(Path path) {
            List<Account> accounts = new ArrayList<>();
            try {
                if (!Files.exists(path)) {
                    Files.createFile(path);
                    return accounts;
                }
                List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }
                    Account account = Account.fromFileString(line);
                    if (account != null) {
                        accounts.add(account);
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading account data: " + e.getMessage());
            }
            return accounts;
        }

        private List<Transaction> readTransactionsFromFile(Path path) {
            List<Transaction> transactions = new ArrayList<>();
            try {
                if (!Files.exists(path)) {
                    Files.createFile(path);
                    return transactions;
                }
                List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }
                    Transaction transaction = Transaction.fromFileString(line);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading transaction data: " + e.getMessage());
            }
            return transactions;
        }
    }
}

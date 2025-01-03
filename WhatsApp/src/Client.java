import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Client {
    private static boolean inCall = false;
    private static String currentCallPartner = null;
    private static Scanner scanner = new Scanner(System.in);
    private static MessageService service;
    private static String name;
    private static CountDownLatch callResponseLatch;
    private static BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
    private static volatile boolean suspendMenu = false;
    private static volatile boolean lockInputForCall = false;

    private static String readInput() throws IOException {
        return inputReader.readLine().trim();
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            service = (MessageService) registry.lookup("MessageService");

            System.out.print("Enter your name: ");
            name = scanner.nextLine();

            ClientCallback callback = new ClientCallbackImpl(new CallHandler());
            service.registerClient(name, callback);

            while (true) {
                try {
                    if (suspendMenu || lockInputForCall) {
                        Thread.sleep(100);
                        continue;
                    }

                    if (!inCall) {
                        displayMenu();
                        String input = readInput();
                        if (suspendMenu || lockInputForCall) continue;

                        int choice = Integer.parseInt(input);

                        switch (choice) {
                            case 1 -> handleSendMessage();
                            case 2 -> handleViewClients();
                            case 3 -> handleCreateGroup();
                            case 4 -> handleJoinGroup();
                            case 5 -> handleLeaveGroup();
                            case 6 -> handleGroupMessage();
                            case 7 -> handleViewGroups();
                            case 8 -> handleMakeCall();
                            case 9 -> handleMakeGroupCall();
                            case 10 -> System.exit(0);
                            default -> System.out.println("Invalid choice. Try again.");
                        }
                    } else if (inCall && currentCallPartner.startsWith("Group: ")) {
                        System.out.print("In call with " + currentCallPartner + ". Type 'LEAVE' to leave the call: ");
                        String input = readInput();
                        if ("LEAVE".equalsIgnoreCase(input)) {
                            String groupName = currentCallPartner.replace("Group: ", "");
                            service.leaveGroupCall(name, groupName);
                            inCall = false;
                            currentCallPartner = null;
                            System.out.println("You have left the group call.");
                        }
                    } else {
                        System.out.print("In call with " + currentCallPartner + ". Type 'END' to end call: ");
                        String input = readInput();
                        if ("END".equalsIgnoreCase(input)) {
                            service.endCall(name, currentCallPartner);
                            inCall = false;
                            currentCallPartner = null;
                        }
                    }
                } catch (NumberFormatException e) {
                    if (!suspendMenu && !lockInputForCall) System.out.println("Invalid choice. Please enter a number.");
                } catch (IOException e) {
                    System.err.println("Error reading input: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("An error occurred: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("Client exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void displayMenu() {
        System.out.println("\n1. Send Message");
        System.out.println("2. View Registered Clients");
        System.out.println("3. Create Group");
        System.out.println("4. Join Group");
        System.out.println("5. Leave Group");
        System.out.println("6. Send Group Message");
        System.out.println("7. View Groups");
        System.out.println("8. Make Voice/Video Call");
        System.out.println("9. Make a Group Call");
        System.out.println("10. Exit");
        System.out.print("Choose an option: ");
    }

    private static void handleMakeGroupCall() throws Exception {
        handleViewGroups();
        System.out.print("Enter group name: ");
        String groupName = scanner.nextLine();
        List<String> groups = service.getGroups(name);
        if (!groups.contains(groupName)) {
            System.out.println("Group not found. Try again.");
            return;
        }
        System.out.println("Initiating group call for " + groupName + "...");
        service.initiateGroupCall(name, groupName);
        inCall = true;
        currentCallPartner = "Group: " + groupName; 
    }


    private static void handleViewClients() throws Exception {
        System.out.println("Registered clients:");
        List<String> clients = service.getRegisteredClients();
        for (String client : clients) {
            System.out.println("- " + client);
        }
    }

    private static void handleMakeCall() throws Exception {
        handleViewClients();
        System.out.print("Enter recipient's name: ");
        String recipient = scanner.nextLine();
        List<String> clients = service.getRegisteredClients();
        if (!clients.contains(recipient)) {
            System.out.println("Recipient not found. Try again.");
            return;
        }
        
        if (name.equals(recipient)) {
            System.out.println("You cannot call yourself.");
            return;
        }

        System.out.println("Calling " + recipient + "...");
        callResponseLatch = new CountDownLatch(1);
        service.initiateCall(name, recipient);
        callResponseLatch.await();
        suspendMenu = false;
        lockInputForCall = false;
    }

    private static void handleSendMessage() throws Exception {
        System.out.println("\nRegistered clients:");
        List<String> clients = service.getRegisteredClients();
        for (String client : clients) {
            System.out.println("- " + client);
        }

        System.out.print("Enter recipient's name: ");
        String recipient = scanner.nextLine();
        if (!clients.contains(recipient)) {
            System.out.println("Recipient not found. Try again.");
            return;
        }

        System.out.print("Enter your message: ");
        String message = scanner.nextLine();

        try {
            String formattedMessage = name + ": " + message;
            String encryptedMessage = EncryptionUtil.encrypt(formattedMessage);
            service.sendMessage(name, recipient, encryptedMessage);
            System.out.println("Encrypted message sent.");
        } catch (Exception e) {
            System.err.println("Error encrypting message: " + e.getMessage());
        }
    }

    private static void handleCreateGroup() throws Exception {
        System.out.print("Enter group name: ");
        String groupName = scanner.nextLine();
        service.createGroup(groupName, name);
    }

    private static void handleJoinGroup() throws Exception {
        System.out.print("Enter group name: ");
        String groupName = scanner.nextLine();
        service.joinGroup(groupName, name);
    }
    
    private static void handleLeaveGroup() throws Exception {
        System.out.println("\nYour groups:");
        List<String> groups = service.getGroups(name);
        for (String group : groups) {
            System.out.println("- " + group);
        }

        System.out.print("Enter group name to leave: ");
        String groupName = scanner.nextLine();

        if (!groups.contains(groupName)) {
            System.out.println("You are not a member of this group.");
            return;
        }
        service.leaveGroup(name, groupName);
        System.out.println("You have left the group: " + groupName);
    }


    private static void handleGroupMessage() throws Exception {
        System.out.println("\nYour groups:");
        List<String> groups = service.getGroups(name);
        for (String group : groups) {
            System.out.println("- " + group);
        }

        System.out.print("Enter group name: ");
        String groupName = scanner.nextLine();
        System.out.print("Enter your message: ");
        String message = scanner.nextLine();

        try {
            String encryptedMessage = EncryptionUtil.encrypt(message);
            service.sendGroupMessage(name, groupName, encryptedMessage);
            System.out.println("Message sent to the server. Waiting for server response...");
        } catch (Exception e) {
            System.err.println("Error encrypting or sending group message: " + e.getMessage());
        }
    }

    private static void handleViewGroups() throws Exception {
        System.out.println("\nYour groups:");
        List<String> groups = service.getGroups(name);
        for (String group : groups) {
            System.out.println("- " + group);
        }
    }
    
    public static void resetSuspendMenu() {
        suspendMenu = false;
    }

    public static void resetLockInputForCall() {
        lockInputForCall = false;
    }

    public static void countDownCallResponseLatch() {
        if (callResponseLatch != null) {
            callResponseLatch.countDown();
        }
    }
    
    public static class CallHandler {
    	public synchronized void handleCallRequest(String caller) {
    	    new Thread(() -> {
    	        suspendMenu = true;
    	        lockInputForCall = true;
    	        try {
    	            System.out.println("\nIncoming call from " + caller);
    	            while (true) {
    	                System.out.print("Accept call? (yes/no): ");
    	                String response = inputReader.readLine().trim().toLowerCase();
    	                if ("yes".equals(response)) {
    	                    if (inCall && currentCallPartner != null) {
    	                        // End the current ongoing call
    	                        service.endCall(name, currentCallPartner);
    	                        System.out.println("Ended the current call to accept a new call.");
    	                    }
    	                    service.respondToCall(caller, name, true);
    	                    inCall = true;
    	                    currentCallPartner = caller;
    	                    System.out.println("Call accepted. You are now in a call with " + caller);
    	                    break;
    	                } else if ("no".equals(response)) {
    	                    service.respondToCall(caller, name, false);
    	                    System.out.println("Call rejected.");
    	                    break;
    	                } else {
    	                    System.out.println("Invalid input. Please respond with 'yes' or 'no'.");
    	                }
    	            }
    	        } catch (IOException e) {
    	            System.err.println("Error handling call request: " + e.getMessage());
    	        } finally {
    	            lockInputForCall = false;
    	            suspendMenu = false;
    	        }
    	    }).start();
    	}

    	public synchronized void handleGroupCallRequest(String caller, String groupName) {
    	    new Thread(() -> {
    	        suspendMenu = true;
    	        lockInputForCall = true;
    	        try {
    	            // Notify the user of the incoming group call
    	            if (!name.equals(caller)) {
    	                System.out.println("\nIncoming group call from " + caller + " for group: " + groupName);
    	                while (true) {
    	                    System.out.print("Accept group call? (yes/no): ");
    	                    String response = inputReader.readLine().trim().toLowerCase();
    	                    if ("yes".equals(response)) {
    	                        // End the current personal call if one exists
    	                        if (inCall && currentCallPartner != null) {
    	                            service.endCall(name, currentCallPartner);
    	                            System.out.println("Ended call with " + currentCallPartner + " to join the group call.");
    	                        }

    	                        // Respond to the group call
    	                        service.respondToGroupCall(name, groupName, true);
    	                        inCall = true;
    	                        currentCallPartner = "Group: " + groupName;
    	                        System.out.println("You joined the group call: " + groupName);
    	                        break;
    	                    } else if ("no".equals(response)) {
    	                        service.respondToGroupCall(name, groupName, false);
    	                        System.out.println("Group call rejected.");
    	                        break;
    	                    } else {
    	                        System.out.println("Invalid input. Please respond with 'yes' or 'no'.");
    	                    }
    	                }
    	            }
    	        } catch (IOException e) {
    	            System.err.println("Error handling group call request: " + e.getMessage());
    	        } finally {
    	            lockInputForCall = false;
    	            suspendMenu = false;
    	        }
    	    }).start();
    	}

        public void handleCallAccepted(String partner) {
            System.out.println("\nCall accepted by " + partner);
            inCall = true;
            currentCallPartner = partner;
            if (callResponseLatch != null) {
                callResponseLatch.countDown();
            }
        }
        
        public void handleYourCallAccepted() {
            System.out.println("\nYou have accepted the call.");
            inCall = true;
        }
        
        public void handleCallRejected(String partner) {
            System.out.println("\nCall rejected by " + partner);
            if (callResponseLatch != null) {
                callResponseLatch.countDown();
            }
        }

        public void handleCallEnded(String partner) {
            System.out.println("\nCall ended with " + partner);
            inCall = false;
            currentCallPartner = null;
        }
    }
}

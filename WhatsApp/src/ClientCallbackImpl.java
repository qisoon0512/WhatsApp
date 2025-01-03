import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

public class ClientCallbackImpl extends UnicastRemoteObject implements ClientCallback {
    private Client.CallHandler callHandler;

    protected ClientCallbackImpl(Client.CallHandler callHandler) throws RemoteException {
        super();
        this.callHandler = callHandler;
    }

    @Override
    public void receiveMessage(String message) throws RemoteException {
        try {
            if (message.startsWith("PLAIN:")) {
                // Handle plain text messages
                System.out.println("\n" + message.substring("PLAIN:".length()).trim());
            } else if (message.startsWith("ERROR:") || message.startsWith("Error:")) {
                // Handle error messages
                System.err.println("\nERROR: " + message.substring(message.indexOf(":") + 1).trim());
                Client.resetSuspendMenu();
                Client.resetLockInputForCall();
            } else if (message.startsWith("CALL_REQUEST:")) {
                // Handle incoming personal call request
                String caller = message.substring("CALL_REQUEST:".length());
                new Thread(() -> callHandler.handleCallRequest(caller)).start();
            } else if (message.startsWith("CALL_ACCEPTED_BY:")) {
                // Handle call acceptance by the partner
                String partner = message.substring("CALL_ACCEPTED_BY:".length());
                callHandler.handleCallAccepted(partner);
            } else if (message.startsWith("CALL_ACCEPTED_YOU")) {
                // Handle the situation where you accept the call
                callHandler.handleYourCallAccepted();
            } else if (message.startsWith("CALL_REJECTED:")) {
                // Handle call rejection
                String partner = message.substring("CALL_REJECTED:".length());
                callHandler.handleCallRejected(partner);
                Client.resetSuspendMenu();
                Client.resetLockInputForCall();
            } else if (message.startsWith("CALL_ENDED:")) {
                // Handle call end notification
                String partner = message.substring("CALL_ENDED:".length());
                callHandler.handleCallEnded(partner);
            } else if (message.startsWith("GROUP_CALL_REQUEST:")) {
                // Handle incoming group call request
                String[] parts = message.split(":");
                String caller = parts[1];
                String groupName = parts[2];
                new Thread(() -> callHandler.handleGroupCallRequest(caller, groupName)).start();
            } else if (message.startsWith("GROUP_CALL_REJECTED:")) {
                // Handle group call rejection notification
                String[] parts = message.split(":");
                String rejector = parts[1];
                String groupName = parts[2];
                System.out.println("\n" + rejector + " has rejected the group call: " + groupName);
            } else if (message.startsWith("GROUP_CALL_ACCEPTED:")) {
                // Handle group call acceptance notification
                String[] parts = message.split(":");
                String accepter = parts[1];
                String groupName = parts[2];
                System.out.println("\n" + accepter + " has joined the group call: " + groupName);
            } else if (message.startsWith("GROUP_CALL_LEFT:")) {
                // Handle notification when a user leaves a group call
                String[] parts = message.split(":");
                String leftUser = parts[1];
                String groupName = parts[2];
                System.out.println("\n" + leftUser + " has left the group call: " + groupName);
            } else if (message.startsWith("GROUP_CALL_ENDED:")) {
                // Handle group call end notification
                System.out.println("\nGroup call ended: " + message.substring("GROUP_CALL_ENDED:".length()));
            } else if (message.startsWith("Group ")) {
                // Handle group messages
                try {
                    int delimiterIndex = message.indexOf(": ");
                    if (delimiterIndex != -1) {
                        String groupInfo = message.substring(0, delimiterIndex);
                        String encryptedData = message.substring(delimiterIndex + 2);
                        String decryptedMessage = EncryptionUtil.decrypt(encryptedData);
                        System.out.println("\n" + groupInfo + ": " + decryptedMessage);
                    } else {
                        System.out.println("Invalid group message format.");
                    }
                } catch (Exception e) {
                    System.err.println("Error decrypting group message: " + e.getMessage());
                }
            } else {
                // Handle personal messages
                try {
                    String encryptedData;
                    if (message.contains(": ")) {
                        encryptedData = message.substring(message.indexOf(": ") + 2);
                    } else {
                        encryptedData = message;
                    }
                    String decryptedMessage = EncryptionUtil.decrypt(encryptedData);
                    System.out.println("\nMessage received: " + decryptedMessage);
                } catch (Exception e) {
                    System.err.println("Error decrypting message: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }

}
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.*;

public class MessageServiceImpl extends UnicastRemoteObject implements MessageService {
    private static final long serialVersionUID = 1L;

    private Map<String, ClientCallback> clients;
    private Map<String, Set<String>> groups;
    private Map<String, String> activeCallSessions;
    private Map<String, Set<String>> activeGroupCalls;

    protected MessageServiceImpl() throws RemoteException {
        super();
        clients = new HashMap<>();
        groups = new HashMap<>();
        activeCallSessions = new HashMap<>();
        activeGroupCalls = new HashMap<>();
    }

    @Override
    public void registerClient(String clientName, ClientCallback callback) throws RemoteException {
        if (!clients.containsKey(clientName)) {
            clients.put(clientName, callback);
            System.out.println(clientName + " has registered.");
        } else {
            System.out.println(clientName + " is already registered.");
        }
    }

    @Override
    public void sendMessage(String sender, String recipient, String message) throws RemoteException {
        if (clients.containsKey(recipient)) {
            String formattedMessage = sender + ": " + message;
            clients.get(recipient).receiveMessage(formattedMessage);
            System.out.println("Message sent from " + sender + " to " + recipient + ": " + message);
        } else {
            System.out.println("Recipient " + recipient + " not found.");
        }
    }

    @Override
    public void createGroup(String groupName, String creator) throws RemoteException {
        if (!groups.containsKey(groupName)) {
            groups.put(groupName, new HashSet<>());
            groups.get(groupName).add(creator);
            System.out.println("Group " + groupName + " created by " + creator);
        } else {
            System.out.println("Group " + groupName + " already exists.");
        }
    }

    @Override
    public void joinGroup(String groupName, String clientName) throws RemoteException {
        if (groups.containsKey(groupName)) {
            groups.get(groupName).add(clientName);
            System.out.println(clientName + " joined group " + groupName);
            if (clients.containsKey(clientName)) {
                clients.get(clientName).receiveMessage("PLAIN:You have successfully joined the group: " + groupName);
            }
        } else {
            System.out.println("Group " + groupName + " does not exist.");
            if (clients.containsKey(clientName)) {
                clients.get(clientName).receiveMessage("PLAIN:Error: The group '" + groupName + "' does not exist.");
            }
        }
    }

    @Override
    public void sendGroupMessage(String sender, String groupName, String message) throws RemoteException {
        if (groups.containsKey(groupName)) {
            for (String member : groups.get(groupName)) {
                if (!member.equals(sender) && clients.containsKey(member)) {
                    clients.get(member).receiveMessage("Group " + groupName + " - " + sender + ": " + message);
                }
            }
            System.out.println("Group message sent: " + sender + " -> Group: " + groupName + ": " + message);
        } else {
            System.out.println("Group message failed: Group " + groupName + " does not exist");
            if (clients.containsKey(sender)) {
                clients.get(sender).receiveMessage("ERROR: Group " + groupName + " does not exist.");
            }
        }
    }

    @Override
    public void initiateGroupCall(String caller, String groupName) throws RemoteException {
        if (groups.containsKey(groupName)) {
            // Create new participants set
            activeGroupCalls.put(groupName, new HashSet<>());
            
            // Add initiator to participants
            activeGroupCalls.get(groupName).add(caller);
            System.out.println(caller + " started group call: " + groupName);
            
            // Send invites to other members
            for (String member : groups.get(groupName)) {
                if (!member.equals(caller) && clients.containsKey(member)) {
                    clients.get(member).receiveMessage("GROUP_CALL_REQUEST:" + caller + ":" + groupName);
                }
            }
        } else {
            clients.get(caller).receiveMessage("ERROR: Group not found");
        }
    }

    @Override
    public void respondToGroupCall(String responder, String groupName, boolean accept) throws RemoteException {
        if (!activeGroupCalls.containsKey(groupName)) return;

        Set<String> participants = activeGroupCalls.get(groupName);

        if (accept) {
            // Check if the responder is in an active personal call
            if (activeCallSessions.containsKey(responder)) {
                String currentPartner = activeCallSessions.get(responder);
                endCall(responder, currentPartner); // End the current personal call
                System.out.println(responder + " ended call with " + currentPartner + " to join group call.");
            }

            if (!participants.contains(responder)) {
                participants.add(responder);
                System.out.println(responder + " joined group call: " + groupName);

                // Notify all participants
                for (String participant : participants) {
                    if (clients.containsKey(participant)) {
                        clients.get(participant).receiveMessage("GROUP_CALL_ACCEPTED:" + responder + ":" + groupName);
                    }
                }
            }
        } else {
            System.out.println(responder + " declined group call: " + groupName);
            // Notify all current participants in the group call about the rejection
            for (String participant : participants) {
                if (clients.containsKey(participant)) {
                    clients.get(participant).receiveMessage("GROUP_CALL_REJECTED:" + responder + ":" + groupName);
                }
            }
        }
    }

    @Override
    public void leaveGroupCall(String client, String groupName) throws RemoteException {
        if (activeGroupCalls.containsKey(groupName)) {
            Set<String> participants = activeGroupCalls.get(groupName);
            if (participants.remove(client)) {
                System.out.println(client + " left the group call for " + groupName);
                // Notify all other participants
                for (String member : participants) {
                    if (clients.containsKey(member)) {
                        clients.get(member).receiveMessage("GROUP_CALL_LEFT:" + client + ":" + groupName);
                    }
                }
            }
            if (participants.isEmpty()) {
                activeGroupCalls.remove(groupName);
                System.out.println("Group call ended: " + groupName);
            }
        }
    }

    @Override
    public void endGroupCall(String caller, String groupName) throws RemoteException {
        if (activeGroupCalls.containsKey(groupName)) {
            Set<String> participants = activeGroupCalls.get(groupName);
            for (String participant : participants) {
                if (clients.containsKey(participant)) {
                    clients.get(participant).receiveMessage("GROUP_CALL_ENDED:" + caller + ":" + groupName);
                }
            }
            activeGroupCalls.remove(groupName);
            System.out.println("Group call ended by " + caller + " for group: " + groupName);
        }      
    }

    @Override
    public List<String> getRegisteredClients() throws RemoteException {
        return new ArrayList<>(clients.keySet());
    }

    @Override
    public List<String> getGroups(String clientName) throws RemoteException {
        List<String> accessibleGroups = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getValue().contains(clientName)) {
                accessibleGroups.add(entry.getKey());
            }
        }
        return accessibleGroups;
    }
    
    @Override
    public void leaveGroup(String client, String groupName) throws RemoteException {
        if (groups.containsKey(groupName)) {
            Set<String> members = groups.get(groupName);
            if (members.remove(client)) {
                System.out.println(client + " left the group: " + groupName);
                for (String member : members) {
                    if (clients.containsKey(member)) {
                        clients.get(member).receiveMessage("PLAIN:" + client + " has left the group: " + groupName);
                    }
                }
            } else {
                System.out.println(client + " is not a member of the group: " + groupName);
                clients.get(client).receiveMessage("ERROR: You are not a member of the group '" + groupName + "'.");
            }
        } else {
            System.out.println("Group not found: " + groupName);
            if (clients.containsKey(client)) {
                clients.get(client).receiveMessage("ERROR: The group '" + groupName + "' does not exist.");
            }
        }
    }

    @Override
    public void initiateCall(String caller, String recipient) throws RemoteException {
        if (clients.containsKey(recipient)) {
            // Notify the recipient about the incoming call
            clients.get(recipient).receiveMessage("CALL_REQUEST:" + caller);
            System.out.println("Call request sent: " + caller + " -> " + recipient);
        } else {
            System.out.println("Call failed: " + recipient + " not found");
            clients.get(caller).receiveMessage("ERROR: Recipient not found");
        }
    }

    @Override
    public void respondToCall(String caller, String recipient, boolean accept) throws RemoteException {
        if (accept) {
            // Check if the recipient is in any group call and remove them
            for (Map.Entry<String, Set<String>> entry : activeGroupCalls.entrySet()) {
                if (entry.getValue().contains(recipient)) {
                    entry.getValue().remove(recipient);
                    // Notify other participants about the removal
                    for (String member : entry.getValue()) {
                        if (clients.containsKey(member)) {
                            clients.get(member).receiveMessage("GROUP_CALL_LEFT:" + recipient + ":" + entry.getKey());
                        }
                    }
                    System.out.println(recipient + " left group call: " + entry.getKey());
                    break; // Assuming a user can only be in one group call
                }
            }

            // Proceed with personal call setup
            activeCallSessions.put(caller, recipient);
            activeCallSessions.put(recipient, caller);
            clients.get(caller).receiveMessage("CALL_ACCEPTED_BY:" + recipient); // Notify caller
            clients.get(recipient).receiveMessage("CALL_ACCEPTED_YOU"); // Notify recipient
            System.out.println("Call accepted: " + caller + " <-> " + recipient);
        } else {
            clients.get(caller).receiveMessage("CALL_REJECTED:" + recipient);
            System.out.println("Call rejected: " + recipient + " -> " + caller);
        }
    }

    @Override
    public void endCall(String caller, String recipient) throws RemoteException {
        if (activeCallSessions.containsKey(caller) && activeCallSessions.get(caller).equals(recipient)) {
            activeCallSessions.remove(caller);
            activeCallSessions.remove(recipient);

            if (clients.containsKey(caller)) {
                clients.get(caller).receiveMessage("CALL_ENDED:" + recipient);
            }
            if (clients.containsKey(recipient)) {
                clients.get(recipient).receiveMessage("CALL_ENDED:" + caller);
            }

            System.out.println("Call ended: " + caller + " <-> " + recipient);
        }
    }
}

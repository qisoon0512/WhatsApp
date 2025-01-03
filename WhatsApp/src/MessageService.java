import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
public interface MessageService extends Remote {
	
    void registerClient(String clientName, ClientCallback callback) throws RemoteException;
    
    void sendMessage(String sender, String recipient, String message) throws RemoteException;
    void createGroup(String groupName, String creator) throws RemoteException;
    void joinGroup(String groupName, String clientName) throws RemoteException;
    void sendGroupMessage(String sender, String groupName, String message) throws RemoteException;

    void initiateCall(String caller, String recipient) throws RemoteException;
    void respondToCall(String caller, String recipient, boolean accept) throws RemoteException;
    void endCall(String caller, String recipient) throws RemoteException;
    
    void initiateGroupCall(String caller, String groupName) throws RemoteException;
    void respondToGroupCall(String caller, String groupName, boolean accept) throws RemoteException;
    void endGroupCall(String caller, String groupName) throws RemoteException;
    void leaveGroupCall(String client, String groupName) throws RemoteException;
    void leaveGroup(String client, String groupName) throws RemoteException;
    
    List<String> getRegisteredClients() throws RemoteException;
    List<String> getGroups(String clientName) throws RemoteException;
    
}
package vn.edu.demo.caro.common.rmi;

import vn.edu.demo.caro.common.model.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface LobbyService extends Remote {

    // Auth
    UserProfile register(String username, String password) throws RemoteException;
    UserProfile login(String username, String password, ClientCallback callback) throws RemoteException;
    void logout(String username) throws RemoteException;

    // Rooms
    String createRoom(String owner, RoomCreateRequest req) throws RemoteException;
    List<RoomInfo> listOpenRooms() throws RemoteException;

    /**
     * Join room. password can be null/blank if room doesn't require password.
     */
    boolean joinRoom(String username, String roomId, String password) throws RemoteException;

    void leaveRoom(String username, String roomId) throws RemoteException;
    void quickPlay(String username) throws RemoteException;

    // Chat
    void sendGlobalChat(ChatMessage msg) throws RemoteException;
    void sendRoomChat(String roomId, ChatMessage msg) throws RemoteException;

    // Gameplay (server authoritative)
    void makeMove(String roomId, String username, int row, int col) throws RemoteException;
    void resign(String roomId, String username) throws RemoteException;

    // Ranking
    List<UserProfile> getLeaderboard(int top) throws RemoteException;

    // Friends
    void sendFriendRequest(FriendRequest req) throws RemoteException;
    void respondFriendRequest(String from, String to, boolean accept) throws RemoteException;
    List<String> getFriends(String username) throws RemoteException;

    // Server info
    List<String> listOnlineUsers() throws RemoteException;

    // Undo / Redo (needs opponent approval)
    void requestUndo(String roomId, String from) throws RemoteException;
    void respondUndo(String roomId, String responder, boolean accept) throws RemoteException;

    void requestRedo(String roomId, String from) throws RemoteException;
    void respondRedo(String roomId, String responder, boolean accept) throws RemoteException;

    // Rematch / Return to lobby
    void requestRematch(String roomId, String from) throws RemoteException;
    void respondRematch(String roomId, String responder, boolean accept) throws RemoteException;

    void returnToLobby(String roomId, String from) throws RemoteException;
}

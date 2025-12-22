package vn.edu.demo.caro.common.rmi;

import vn.edu.demo.caro.common.model.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ClientCallback extends Remote {

    // -------- Global events --------
    void onGlobalChat(ChatMessage msg) throws RemoteException;
    void onAnnouncement(String text) throws RemoteException;
    void onWarning(String text) throws RemoteException;
    void onBanned(String reason) throws RemoteException;

    void onRoomListUpdated(List<RoomInfo> rooms) throws RemoteException;
    void onOnlineUsersUpdated(List<String> users) throws RemoteException;
    void onLeaderboardUpdated(List<UserProfile> leaderboard) throws RemoteException;

    // -------- Friends --------
    void onFriendRequest(FriendRequest req) throws RemoteException;
    void onFriendListUpdated(List<String> friends) throws RemoteException;

    // -------- Room chat (authoritative via server) --------
    void onRoomChat(String roomId, ChatMessage msg) throws RemoteException;

    // -------- Game (authoritative via server) --------
    void onGameStarted(GameStart start) throws RemoteException;
    void onGameUpdated(GameUpdate update) throws RemoteException;
    void onGameEnded(GameEnd end) throws RemoteException;

    // -------- Undo / Redo (request + result) --------
    void onUndoRequested(String roomId, String from) throws RemoteException;
    void onUndoResult(String roomId, boolean accepted, String message) throws RemoteException;

    void onRedoRequested(String roomId, String from) throws RemoteException;
    void onRedoResult(String roomId, boolean accepted, String message) throws RemoteException;

    // -------- Rematch / Return to lobby --------
    void onRematchRequested(String roomId, String from) throws RemoteException;
    void onReturnToLobby(String roomId, String message) throws RemoteException;
}

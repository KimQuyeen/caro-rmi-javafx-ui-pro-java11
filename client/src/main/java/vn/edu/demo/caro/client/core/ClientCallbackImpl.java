package vn.edu.demo.caro.client.core;

import javafx.application.Platform;
import vn.edu.demo.caro.client.controller.GameController;
import vn.edu.demo.caro.client.controller.MainController;
import vn.edu.demo.caro.client.controller.view.ChatViewController;
import vn.edu.demo.caro.client.controller.view.FriendsViewController;
import vn.edu.demo.caro.client.controller.view.LeaderboardViewController;
import vn.edu.demo.caro.client.controller.view.RoomsViewController;
import vn.edu.demo.caro.common.model.*;
import vn.edu.demo.caro.common.rmi.ClientCallback;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * RMI callback: Server -> Client
 * All UI updates must be executed on JavaFX Application Thread.
 */
public class ClientCallbackImpl extends UnicastRemoteObject implements ClientCallback {

    private final AppContext ctx;

    private volatile MainController mainController;
    private volatile RoomsViewController roomsController;
    private volatile ChatViewController chatController;
    private volatile FriendsViewController friendsController;
    private volatile LeaderboardViewController leaderboardController;
    private volatile GameController gameController;

    public ClientCallbackImpl(AppContext ctx) throws RemoteException {
        super(0);
        this.ctx = ctx;
    }

    // ===== Bind methods used by controllers =====
    public void bindMain(MainController c) { this.mainController = c; }
    public void bindRooms(RoomsViewController c) { this.roomsController = c; }
    public void bindChat(ChatViewController c) { this.chatController = c; }
    public void bindFriends(FriendsViewController c) { this.friendsController = c; }
    public void bindLeaderboard(LeaderboardViewController c) { this.leaderboardController = c; }
    public void bindGame(GameController c) { this.gameController = c; }

    // -------- Global events --------
    @Override
    public void onGlobalChat(ChatMessage msg) throws RemoteException {
        Platform.runLater(() -> {
            if (chatController != null) chatController.append(msg);
        });
    }

    @Override
    public void onAnnouncement(String text) throws RemoteException {
        Platform.runLater(() -> {
            if (mainController != null) mainController.pushStatus("Thông báo: " + text);
        });
    }

    @Override
    public void onWarning(String text) throws RemoteException {
        Platform.runLater(() -> {
            if (mainController != null) mainController.pushStatus("Cảnh báo: " + text);
        });
    }

    @Override
    public void onBanned(String reason) throws RemoteException {
        Platform.runLater(() -> {
            if (mainController != null) mainController.pushStatus("Bị ban: " + reason);
        });
    }

    @Override
    public void onRoomListUpdated(List<RoomInfo> rooms) throws RemoteException {
        Platform.runLater(() -> {
            ctx.rooms.clear();
            if (rooms != null) ctx.rooms.addAll(rooms);

            if (roomsController != null) roomsController.refreshRooms();
        });
    }

    @Override
    public void onOnlineUsersUpdated(List<String> users) throws RemoteException {
        Platform.runLater(() -> {
            ctx.onlineUsers.clear();
            if (users != null) ctx.onlineUsers.addAll(users);

            if (friendsController != null) friendsController.refresh();
            if (roomsController != null) roomsController.refreshOnline();
        });
    }

    @Override
    public void onLeaderboardUpdated(List<UserProfile> leaderboard) throws RemoteException {
        Platform.runLater(() -> {
            ctx.leaderboard.clear();
            if (leaderboard != null) ctx.leaderboard.addAll(leaderboard);

            if (leaderboardController != null) leaderboardController.refresh();
        });
    }

    // -------- Friends --------
    @Override
    public void onFriendRequest(FriendRequest req) throws RemoteException {
        Platform.runLater(() -> {
            if (friendsController != null) friendsController.handleFriendRequest(req);
        });
    }

    @Override
    public void onFriendListUpdated(List<String> friends) throws RemoteException {
        Platform.runLater(() -> {
            ctx.friends.clear();
            if (friends != null) ctx.friends.addAll(friends);

            if (friendsController != null) friendsController.refresh();
        });
    }

    // -------- Room chat --------
    @Override
    public void onRoomChat(String roomId, ChatMessage msg) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onRoomChat(msg);
        });
    }

    // -------- Game --------
    @Override
    public void onGameStarted(GameStart start) throws RemoteException {
        Platform.runLater(() -> {
            // QUAN TRỌNG:
            // Lúc này user vẫn đang ở Main/Rooms, gameController thường chưa tồn tại.
            // => lưu pending rồi chuyển scene sang Game.
            ctx.currentRoomId = start.getRoomId();
            ctx.stage.getProperties().put("pending.gameStart", start);

            // Chỉ chuyển sang màn game khi đủ 2 người (server mới gọi callback này)
            ctx.sceneManager.showGame();
        });
    }

    @Override
    public void onGameUpdated(GameUpdate update) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onGameUpdate(update);
        });
    }

    @Override
    public void onGameEnded(GameEnd end) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onGameEnd(end);
        });
    }

    // -------- Undo / Redo --------
    @Override
    public void onUndoRequested(String roomId, String from) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onUndoRequested(roomId, from);
        });
    }

    @Override
    public void onUndoResult(String roomId, boolean accepted, String message) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onUndoResult(roomId, accepted, message);
        });
    }

    @Override
    public void onRedoRequested(String roomId, String from) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onRedoRequested(roomId, from);
        });
    }

    @Override
    public void onRedoResult(String roomId, boolean accepted, String message) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onRedoResult(roomId, accepted, message);
        });
    }

    // -------- Rematch / Return to lobby --------
    @Override
    public void onRematchRequested(String roomId, String from) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onRematchRequested(roomId, from);
        });
    }

    @Override
    public void onReturnToLobby(String roomId, String message) throws RemoteException {
        Platform.runLater(() -> {
            if (gameController != null) gameController.onReturnToLobby(roomId, message);
        });
    }
}

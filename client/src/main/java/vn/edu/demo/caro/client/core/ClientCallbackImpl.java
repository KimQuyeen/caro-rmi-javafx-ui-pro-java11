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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RMI callback: Server -> Client
 * UI updates must run on JavaFX Application Thread.
 *
 * FIX: fx() dùng QUEUE để không làm rơi callback (tránh “đứng”).
 */
public class ClientCallbackImpl extends UnicastRemoteObject implements ClientCallback {

    private final AppContext ctx;

    private volatile MainController mainController;
    private volatile RoomsViewController roomsController;
    private volatile ChatViewController chatController;
    private volatile FriendsViewController friendsController;
    private volatile LeaderboardViewController leaderboardController;
    private volatile GameController gameController;

    // ===== FX queue (FIX) =====
    private final ConcurrentLinkedQueue<Runnable> fxQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean fxScheduled = new AtomicBoolean(false);

    private void fx(Runnable r) {
        if (r == null) return;
        fxQueue.add(r);
        if (fxScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::drainFx);
        }
    }

    private void drainFx() {
        try {
            Runnable task;
            while ((task = fxQueue.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        } finally {
            fxScheduled.set(false);
            // Nếu trong lúc drain có task mới đến
            if (!fxQueue.isEmpty()) {
                fx(() -> { /* noop */ });
            }
        }
    }

    // ===== Coalesce game update/snapshot (optional, but good) =====
    private final AtomicReference<GameUpdate> latestUpdate = new AtomicReference<>();
    private final AtomicBoolean updateScheduled = new AtomicBoolean(false);

    private final AtomicReference<GameSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicBoolean snapshotScheduled = new AtomicBoolean(false);

    private final AtomicBoolean switchingToGame = new AtomicBoolean(false);

    public ClientCallbackImpl(AppContext ctx) throws RemoteException {
        super(0); // random port
        this.ctx = ctx;
    }

    // ===== Bind methods used by controllers =====
    public void bindMain(MainController c) { this.mainController = c; }
    public void bindRooms(RoomsViewController c) { this.roomsController = c; }
    public void bindChat(ChatViewController c) { this.chatController = c; }
    public void bindFriends(FriendsViewController c) { this.friendsController = c; }
    public void bindLeaderboard(LeaderboardViewController c) { this.leaderboardController = c; }
    public void bindGame(GameController c) { this.gameController = c; }

    private void scheduleUpdateDrain() {
        if (updateScheduled.compareAndSet(false, true)) {
            fx(() -> {
                try {
                    GameUpdate u = latestUpdate.getAndSet(null);
                    if (u != null && gameController != null) {
                        gameController.onGameUpdate(u); // GameController đã tự fx-safe
                    }
                } finally {
                    updateScheduled.set(false);
                    if (latestUpdate.get() != null) scheduleUpdateDrain();
                }
            });
        }
    }

    private void scheduleSnapshotDrain() {
        if (snapshotScheduled.compareAndSet(false, true)) {
            fx(() -> {
                try {
                    GameSnapshot s = latestSnapshot.getAndSet(null);
                    if (s != null && gameController != null) {
                        gameController.applySnapshot(s); // GameController đã tự fx-safe
                    }
                } finally {
                    snapshotScheduled.set(false);
                    if (latestSnapshot.get() != null) scheduleSnapshotDrain();
                }
            });
        }
    }


   

    // Hàm hiện popup hỏi ý kiến
    private void showFriendRequestDialog(FriendRequest req) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Lời mời kết bạn");
        alert.setHeaderText("Bạn nhận được lời mời kết bạn!");
        alert.setContentText(req.getFrom() + " muốn kết bạn với bạn.");

        javafx.scene.control.ButtonType btnAccept = new javafx.scene.control.ButtonType("Đồng ý", javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType btnDecline = new javafx.scene.control.ButtonType("Từ chối", javafx.scene.control.ButtonBar.ButtonData.NO);
        
        alert.getButtonTypes().setAll(btnAccept, btnDecline);

        // Xử lý sự kiện khi bấm nút
        alert.showAndWait().ifPresent(type -> {
            boolean accept = (type == btnAccept);
            
            // Gửi phản hồi về server (dùng thread riêng của AppContext)
            ctx.io().execute(() -> {
                try {
                    ctx.lobby.respondFriendRequest(req.getFrom(), ctx.username, accept);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
    }

    // -------- Global events --------
    @Override
    public void onGlobalChat(ChatMessage msg) throws RemoteException {
        if (msg == null) return;
        fx(() -> ctx.getGlobalChatStore().add(msg));
    }

    @Override
    public void onAnnouncement(String text) throws RemoteException {
        fx(() -> {
            if (mainController != null) mainController.pushStatus("Thông báo: " + text);
            if (gameController != null) gameController.onAnnouncement(text);
        });
    }

    @Override
    public void onWarning(String text) throws RemoteException {
        fx(() -> { if (mainController != null) mainController.pushStatus("Cảnh báo: " + text); });
    }

    @Override
    public void onBanned(String reason) throws RemoteException {
        fx(() -> { if (mainController != null) mainController.pushStatus("Bị ban: " + reason); });
    }

    @Override
    public void onRoomListUpdated(List<RoomInfo> rooms) throws RemoteException {
        fx(() -> {
            ctx.rooms.clear();
            if (rooms != null) ctx.rooms.addAll(rooms);
            if (roomsController != null) roomsController.refreshRooms();
        });
    }

    @Override
    public void onOnlineUsersUpdated(List<String> users) throws RemoteException {
        fx(() -> {
            ctx.onlineUsers.clear();
            if (users != null) ctx.onlineUsers.addAll(users);
            if (friendsController != null) friendsController.refresh();
            if (roomsController != null) roomsController.refreshOnline();
        });
    }

    @Override
    public void onLeaderboardUpdated(List<UserProfile> leaderboard) throws RemoteException {
        fx(() -> {
            ctx.leaderboard.clear();
            if (leaderboard != null) ctx.leaderboard.addAll(leaderboard);
            if (leaderboardController != null) leaderboardController.refresh();
        });
    }

    // -------- Friends --------
    // @Override
    // public void onFriendRequest(FriendRequest req) throws RemoteException {
    //     fx(() -> { if (friendsController != null) friendsController.handleFriendRequest(req); });
    // }

    @Override
    public void onFriendListUpdated(List<String> friends) throws RemoteException {
        fx(() -> {
            ctx.friends.clear();
            if (friends != null) ctx.friends.addAll(friends);
            if (friendsController != null) friendsController.refresh();
        });
    }

     @Override
    public void onFriendRequest(FriendRequest req) throws RemoteException {
        // Cập nhật danh sách bên view bạn bè (nếu đang mở)
        fx(() -> { 
            if (friendsController != null) friendsController.handleFriendRequest(req); 
            
            // THÊM ĐOẠN NÀY: Hiện Popup thông báo ngay lập tức
            showFriendRequestDialog(req);
        });
    }

    // -------- Room chat --------
    @Override
    public void onRoomChat(String roomId, ChatMessage msg) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onRoomChat(msg); });
    }

    // -------- Game --------
    @Override
    public void onGameStarted(GameStart start) throws RemoteException {
        if (start == null) return;

        fx(() -> {
            ctx.currentRoomId = start.getRoomId();
            ctx.stage.getProperties().put("pending.gameStart", start);

            if (switchingToGame.compareAndSet(false, true)) {
                try {
                    ctx.sceneManager.showGame();
                } finally {
                    Platform.runLater(() -> switchingToGame.set(false));
                }
            }
        });
    }

    @Override
    public void onGameUpdated(GameUpdate update) throws RemoteException {
        if (update == null) return;
        latestUpdate.set(update);
        scheduleUpdateDrain();
    }

    @Override
    public void onGameEnded(GameEnd end) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onGameEnd(end); });
    }

    // -------- Board snapshot (authoritative) --------
    @Override
    public void onBoardReset(GameSnapshot snapshot) throws RemoteException {
        if (snapshot == null) return;
        latestSnapshot.set(snapshot);
        scheduleSnapshotDrain();
    }

    // -------- Undo / Redo --------
    @Override
    public void onUndoRequested(String roomId, String from) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onUndoRequested(roomId, from); });
    }

    @Override
    public void onUndoResult(String roomId, boolean accepted, String message) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onUndoResult(roomId, accepted, message); });
    }

    @Override
    public void onRedoRequested(String roomId, String from) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onRedoRequested(roomId, from); });
    }

    @Override
    public void onRedoResult(String roomId, boolean accepted, String message) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onRedoResult(roomId, accepted, message); });
    }

    // -------- Rematch / Return to lobby --------
    @Override
    public void onRematchRequested(String roomId, String from) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onRematchRequested(roomId, from); });
    }

    @Override
    public void onReturnToLobby(String roomId, String message) throws RemoteException {
        fx(() -> { if (gameController != null) gameController.onReturnToLobby(roomId, message); });
    }
}

package vn.edu.demo.caro.server.service;

import vn.edu.demo.caro.common.model.*;
import vn.edu.demo.caro.common.model.Enums.GameEndReason;
import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.model.Enums.PostGameChoice;
import vn.edu.demo.caro.common.model.Enums.RoomStatus;
import vn.edu.demo.caro.common.rmi.ClientCallback;
import vn.edu.demo.caro.common.rmi.LobbyService;
import vn.edu.demo.caro.server.dao.UserDao;
import vn.edu.demo.caro.server.state.Room;
import vn.edu.demo.caro.server.state.ServerState;
import vn.edu.demo.caro.common.model.UserPublicProfile.FriendStatus;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LobbyServiceImpl extends UnicastRemoteObject implements LobbyService {

    private final ServerState state;

    private final ExecutorService callbackExecutor = Executors.newCachedThreadPool();
    // ===== Pending decisions (approval-based) =====
    private final Map<String, PendingDecision> pendingUndo = new HashMap<>();
    // "Redo" giờ là "đề nghị hòa" nhưng vẫn dùng pendingRedo để không phải đổi interface
    private final Map<String, PendingDecision> pendingRedo = new HashMap<>();
    private final Map<String, PendingDecision> pendingRematch = new HashMap<>();
// private boolean waitingRematchDecision = false;

// ===== Global chat history =====
private static final int GLOBAL_CHAT_MAX = 200;
private final Deque<ChatMessage> globalChatHistory = new ArrayDeque<>();

    // ===== Move history =====
    // NOTE: redoStack không còn dùng cho "Redo=Draw offer", nhưng giữ lại để không vỡ code cũ
    private final Map<String, Deque<MoveRecord>> moveHistory = new HashMap<>();
    private final Map<String, Deque<MoveRecord>> redoStack = new HashMap<>();

    // post-game choice: roomId -> (username -> choice)
    private final Map<String, Map<String, PostGameChoice>> postGameChoices = new HashMap<>();

    // ===== Turn timeout checker =====
    private final ScheduledExecutorService timeoutExec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "turn-timeout-checker");
                t.setDaemon(true);
                return t;
            });

    public LobbyServiceImpl(ServerState state) throws RemoteException {
        super(0);
        this.state = state;
        startTimeoutChecker();
    }

    // ============================================================
    // Timeout
    // ============================================================
    private void startTimeoutChecker() {
        timeoutExec.scheduleAtFixedRate(() -> {
            try {
                checkAllRoomsForTimeout();
            } catch (Exception ignored) {
            }
        }, 300, 300, TimeUnit.MILLISECONDS);
    }

    private void checkAllRoomsForTimeout() throws RemoteException {
        synchronized (this) {
            long now = System.currentTimeMillis();

            for (Room room : state.rooms.values()) {
                if (room == null) continue;
                if (room.status != RoomStatus.PLAYING) continue;
                if (!room.timed) continue;

                String turnUser = room.turn;
                if (turnUser == null || turnUser.isBlank()) continue;

                long deadline = room.turnDeadlineMillis;
                if (deadline <= 0) continue;

                if (now > deadline) {
                    String loser = turnUser;
                    String winner = opponentOf(room, loser);

                    room.turnDeadlineMillis = 0L; // chặn gọi nhiều lần
                    endGame(room, winner, GameEndReason.TIMEOUT);
                    broadcastRooms();
                }
            }
        }
    }

    // ============================================================
    // Rematch / Return to lobby (legacy API)
    // ============================================================
    @Override
    public synchronized void requestRematch(String roomId, String from) throws RemoteException {
        submitPostGameChoice(roomId, from, Enums.PostGameChoice.REMATCH);
    }

    @Override
    public synchronized void respondRematch(String roomId, String responder, boolean accept) throws RemoteException {
        submitPostGameChoice(roomId, responder, accept ? Enums.PostGameChoice.REMATCH : Enums.PostGameChoice.RETURN);
    }

    // ============================================================
    // Auth
    // ============================================================
    @Override
    public synchronized UserProfile register(String username, String password) throws RemoteException {
        try {
            if (username == null || username.trim().isEmpty())
                throw new RemoteException("Username trống");
            if (password == null || password.trim().isEmpty())
                throw new RemoteException("Password trống");

            username = username.trim();

            if (state.userDao.exists(username)) {
                throw new RemoteException("Username đã tồn tại");
            }

            state.userDao.create(username, password);

            var rec = state.userDao.find(username)
                    .orElseThrow(() -> new RemoteException("Không đọc được user vừa tạo"));

            return new UserProfile(rec.username, rec.wins, rec.losses, rec.draws, rec.elo);

        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("Register error: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized UserProfile login(String username, String password, ClientCallback callback) throws RemoteException {
        try {
            Objects.requireNonNull(username);
            Objects.requireNonNull(password);
            Objects.requireNonNull(callback);

            username = username.trim();

            state.userDao.ensureUser(username, password);

            var recOpt = state.userDao.find(username);
            if (recOpt.isEmpty()) throw new RemoteException("Không đọc được user.");

            var rec = recOpt.get();
            if (!rec.password.equals(password)) {
                throw new RemoteException("Sai mật khẩu (demo).");
            }
            if (rec.bannedUntil != null && Instant.now().isBefore(rec.bannedUntil)) {
                throw new RemoteException("Tài khoản đang bị ban đến " + rec.bannedUntil + " | Lý do: " + rec.banReason);
            }

            state.online.put(username, new vn.edu.demo.caro.server.state.OnlineSession(username, callback));

            callback.onOnlineUsersUpdated(sortedOnlineUsers());
            callback.onRoomListUpdated(allRoomsInfo());
            callback.onLeaderboardUpdated(state.userDao.topElo(50));
            callback.onFriendListUpdated(state.friendDao.listFriends(username));

            broadcastOnlineUsers();
            return new UserProfile(rec.username, rec.wins, rec.losses, rec.draws, rec.elo);
        } catch (SQLException e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void logout(String username) throws RemoteException {
        state.online.remove(username);
        for (Room r : new ArrayList<>(state.rooms.values())) {
            if (r.players.contains(username)) leaveRoom(username, r.id);
        }
        broadcastOnlineUsers();
        broadcastRooms();
    }

    // ============================================================
    // Rooms
    // ============================================================
    @Override
    public synchronized String createRoom(String owner, RoomCreateRequest req) throws RemoteException {
        requireOnline(owner);
        owner = owner.trim();

        if (req == null) throw new RemoteException("Thiếu thông tin tạo phòng.");
        if (req.getRoomName() == null || req.getRoomName().trim().isEmpty())
            throw new RemoteException("Tên phòng không được trống.");

        int boardSize = req.getBoardSize();
        if (boardSize < 10 || boardSize > 30)
            throw new RemoteException("Kích thước bàn cờ không hợp lệ (10..30).");

        if (req.isTimed() && req.getTimeLimitSeconds() <= 0)
            throw new RemoteException("Thời gian mỗi lượt không hợp lệ.");

        if (req.isPasswordEnabled()) {
            String pw = req.getPassword();
            if (pw == null || pw.trim().isEmpty())
                throw new RemoteException("Mật khẩu phòng không hợp lệ.");
        }

        String id = UUID.randomUUID().toString();
        Room room = new Room(id, owner, req);

        if (!room.players.contains(owner)) room.players.add(owner);
        room.status = RoomStatus.WAITING;

        state.rooms.put(id, room);
        moveHistory.put(id, new ArrayDeque<>());
        redoStack.put(id, new ArrayDeque<>());

        broadcastRooms();
        return id;
    }

    @Override
    public List<RoomInfo> listOpenRooms() throws RemoteException {
        return state.rooms.values().stream()
                .filter(r -> r.status == RoomStatus.WAITING && r.players.size() < 2)
                .map(this::toInfo)
                .sorted(Comparator.comparing(RoomInfo::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public synchronized boolean joinRoom(String username, String roomId, String password) throws RemoteException {
        requireOnline(username);
        username = username.trim();

        Room room = state.rooms.get(roomId);
        if (room == null) throw new RemoteException("Room không tồn tại.");

        if (room.status != RoomStatus.WAITING) return false;
        if (room.isFull()) return false;

        if (room.hasPassword) {
            if (password == null || password.trim().isEmpty()) throw new RemoteException("Phòng yêu cầu mật khẩu.");
            if (!Objects.equals(room.password, password)) throw new RemoteException("Mật khẩu phòng không đúng.");
        }

        if (!room.players.contains(username)) room.players.add(username);

        if (room.players.size() == 2) {
            // ===== START MATCH (AUTHORITATIVE) =====
            room.status = RoomStatus.PLAYING;

            String p1 = room.players.get(0);
            String p2 = room.players.get(1);

            boolean p1IsX = ThreadLocalRandom.current().nextBoolean();
            room.playerX = p1IsX ? p1 : p2;
            room.playerO = p1IsX ? p2 : p1;

            room.moveNo = 0;
            clearBoard(room);

            moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();
            redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();
            pendingUndo.remove(roomId);
            pendingRedo.remove(roomId);
            postGameChoices.remove(roomId);

            room.turn = room.playerX;
            resetDeadline(room);

            pushGameStart(room, room.playerX, Mark.X, true);
            pushGameStart(room, room.playerO, Mark.O, false);

            broadcastSnapshot(room);
        }

        broadcastRooms();
        return true;
    }

    @Override
public synchronized void leaveRoom(String username, String roomId) throws RemoteException {
    Room room = state.rooms.get(roomId);
    if (room == null) return;

    final String leaver = (username == null ? null : username.trim());
    room.players.remove(leaver);

    if (room.status == RoomStatus.PLAYING && room.players.size() == 1) {
        final String remaining = room.players.get(0);

        safeCallback(remaining, cb -> cb.onGameEnded(new GameEnd(roomId, remaining, GameEndReason.ABORT)));
        safeCallback(remaining, cb -> cb.onAnnouncement(leaver + " đã rời phòng và về lobby. Đang chờ người chơi khác vào..."));

        if (Objects.equals(room.owner, leaver)) {
            room.owner = remaining;
        }

        resetRoomToWaiting(roomId, room);
        broadcastRooms();
        return;
    }



    if (room.players.isEmpty()) {
        cleanupRoom(roomId);
        state.rooms.remove(roomId);
        broadcastRooms();
        return;
    }

    room.status = RoomStatus.WAITING;
    broadcastRooms();
}

private void resetRoomToWaiting(String roomId, Room room) throws RemoteException {
    // clear post-game / pending
    pendingUndo.remove(roomId);
    pendingRedo.remove(roomId);
    pendingRematch.remove(roomId);
    postGameChoices.remove(roomId);

    // reset game state
    room.status = RoomStatus.WAITING;
    room.moveNo = 0;
    room.turn = null;                 // chưa có lượt vì chưa đủ 2 người
    room.turnDeadlineMillis = 0L;

    clearBoard(room);

    moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();
    redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();

    // đẩy snapshot để client thấy bàn cờ trống + disabled (do status WAITING)
    broadcastSnapshot(room);
}

    @Override
    public synchronized void quickPlay(String username) throws RemoteException {
        requireOnline(username);

        Optional<Room> waiting = state.rooms.values().stream()
                .filter(r -> r.status == RoomStatus.WAITING && r.players.size() == 1 && !r.owner.equals(username))
                .findFirst();

        if (waiting.isPresent()) {
            joinRoom(username, waiting.get().id, null);
            return;
        }

        RoomCreateRequest req = new RoomCreateRequest();
        req.setRoomName("QuickPlay-" + username);
        req.setBoardSize(15);
        req.setBlockTwoEnds(false);
        req.setPasswordEnabled(false);
        req.setPassword(null);
        req.setTimed(false);
        req.setTimeLimitSeconds(0);

        createRoom(username, req);
    }

    // ============================================================
    // Chat
    // ============================================================
@Override
public void sendGlobalChat(ChatMessage msg) throws RemoteException {
    final List<String> users;
    synchronized (this) {
        if (msg == null) return;

        globalChatHistory.addLast(msg);
        while (globalChatHistory.size() > GLOBAL_CHAT_MAX) globalChatHistory.removeFirst();

        users = sortedOnlineUsers();
    }
    for (String u : users) safeCallback(u, cb -> cb.onGlobalChat(msg));
}


   @Override
public void sendRoomChat(String roomId, ChatMessage msg) throws RemoteException {
    final List<String> targets;
    synchronized (this) {
        Room room = state.rooms.get(roomId);
        if (room == null) throw new RemoteException("Room không tồn tại.");
        targets = new ArrayList<>(room.players);
    }
    // callback ngoài synchronized(this)
    for (String u : targets) {
        safeCallback(u, cb -> cb.onRoomChat(roomId, msg));
    }
}

    // ============================================================
    // Gameplay
    // ============================================================
    @Override
    public synchronized void makeMove(String roomId, String username, int row, int col) throws RemoteException {
        Room room = state.rooms.get(roomId);

        // có nước mới thì hủy pending (Undo/Draw offer) cũ để tránh trạng thái treo
        pendingUndo.remove(roomId);
        pendingRedo.remove(roomId);

        if (room == null) throw new RemoteException("Room không tồn tại.");
        if (room.status != RoomStatus.PLAYING) throw new RemoteException("Room không ở trạng thái PLAYING.");
        if (username == null) throw new RemoteException("User null.");

        username = username.trim();

        if (!room.players.contains(username)) throw new RemoteException("Bạn không ở trong phòng.");
        if (!Objects.equals(room.turn, username)) {
            throw new RemoteException("Chưa đến lượt bạn. Lượt hiện tại: " + room.turn);
        }

        if (!inBounds(room, row, col)) throw new RemoteException("Nước đi ngoài bàn cờ.");
        if (room.board[row][col] != Mark.EMPTY) throw new RemoteException("Ô đã được đánh.");

        // new move => clear redoStack legacy
        redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();

        Mark mark = username.equals(room.playerX) ? Mark.X : Mark.O;
        room.board[row][col] = mark;

        int moveNo = ++room.moveNo;

        moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>())
                .push(new MoveRecord(row, col, mark, username, moveNo));

        String next = username.equals(room.playerX) ? room.playerO : room.playerX;
        room.turn = next;
        resetDeadline(room);

        // push update nhanh
        Move mv = new Move(row, col, moveNo, username);
        GameUpdate update = new GameUpdate(roomId, mv, mark, next);
        for (String u : new ArrayList<>(room.players)) safeCallback(u, cb -> cb.onGameUpdated(update));

        // snapshot authoritative
        broadcastSnapshot(room);

        // win/draw
        if (isWin(room, row, col, mark)) {
            endGame(room, username, GameEndReason.WIN);
        } else if (room.moveNo >= room.boardSize * room.boardSize) {
            endGame(room, null, GameEndReason.DRAW);
        }

        broadcastRooms();
    }

    @Override
    public synchronized void resign(String roomId, String username) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) return;
        if (room.status != RoomStatus.PLAYING) return;

        String winner = room.players.stream().filter(p -> !p.equals(username)).findFirst().orElse(null);
        endGame(room, winner, GameEndReason.RESIGN);
        broadcastRooms();
    }

    // ============================================================
    // Undo (approval): rollback về "nước cuối của người xin"
    // ============================================================
    @Override
public synchronized void requestUndo(String roomId, String from) throws RemoteException {
    Room room = mustPlayingRoom(roomId);

    final String fromUser = (from == null ? null : from.trim());
    requirePlayer(room, fromUser);

    final String opp = opponentOf(room, fromUser);
    if (opp == null) {
        safeCallback(fromUser, cb -> cb.onUndoResult(roomId, false, "Không có đối thủ để xin Undo."));
        return;
    }

    if (pendingUndo.get(roomId) != null) {
        safeCallback(fromUser, cb -> cb.onUndoResult(roomId, false, "Đang có yêu cầu Undo chờ xử lý."));
        return;
    }

    UndoPlan plan = computeUndoPlan(roomId, room, fromUser, opp);
    if (!plan.allowed) {
        safeCallback(fromUser, cb -> cb.onUndoResult(roomId, false, plan.reason));
        return;
    }

    pendingUndo.put(roomId, new PendingDecision(fromUser, opp));
    safeCallback(opp, cb -> cb.onUndoRequested(roomId, fromUser));
    safeCallback(fromUser, cb -> cb.onUndoResult(roomId, true, "Đã gửi yêu cầu Undo. Chờ đối thủ phản hồi..."));
}


  @Override
public synchronized void respondUndo(String roomId, String responder, boolean accept) throws RemoteException {
    Room room = state.rooms.get(roomId);
    if (room == null) return;

    // Chuẩn hoá responder chỉ 1 lần
    String responderUser = (responder == null ? null : responder.trim());
    requirePlayer(room, responderUser);

    PendingDecision pending = pendingUndo.get(roomId);
    if (pending == null) {
        safeCallback(responderUser, cb -> cb.onUndoResult(roomId, false, "Không có yêu cầu Undo nào để phản hồi."));
        return;
    }

    // chỉ người nhận request mới được phản hồi
    if (!Objects.equals(pending.to, responderUser)) {
        safeCallback(responderUser, cb -> cb.onUndoResult(roomId, false, "Bạn không phải người nhận yêu cầu Undo này."));
        return;
    }

    String requester = pending.from;
    pendingUndo.remove(roomId);

    // tạo biến final để dùng trong lambda (tránh lỗi effectively final)
    final String requesterUser = requester;
    final String responderFinal = responderUser;
    final String opp = responderFinal; // người đối diện trong flow này chính là responder

    if (!accept) {
        safeCallback(requesterUser, cb -> cb.onUndoResult(roomId, false, responderFinal + " đã từ chối Undo."));
        safeCallback(responderFinal, cb -> cb.onUndoResult(roomId, true, "Bạn đã từ chối Undo."));
        return;
    }

    // Tính kế hoạch undo theo rule của bạn (ví dụ: xoá X của đối thủ + O của requester)
    UndoPlan plan = computeUndoPlan(roomId, room, requesterUser, opp);
    if (!plan.allowed) {
        safeCallback(requesterUser, cb -> cb.onUndoResult(roomId, false, "Không thể Undo: " + plan.reason));
        safeCallback(responderFinal, cb -> cb.onUndoResult(roomId, false, "Không thể Undo: " + plan.reason));
        return;
    }

    List<MoveRecord> removed = applyUndoRollback(roomId, room, requesterUser, plan.rollbackCount);

    String detail = removed.stream()
            .map(r -> r.by + ":(" + r.row + "," + r.col + ")")
            .collect(Collectors.joining(", "));

    for (String u : new ArrayList<>(room.players)) {
        safeCallback(u, cb -> cb.onUndoResult(roomId, true,
                "Undo được chấp nhận. Đã xóa: " + detail + ". Lượt: " + room.turn));
    }

    broadcastRooms();
}

    // ============================================================
    // "Redo" (approval): đổi nghĩa thành ĐỀ NGHỊ HÒA (DRAW OFFER)
    // ============================================================
    @Override
public synchronized void requestRedo(String roomId, String from) throws RemoteException {
    Room room = mustPlayingRoom(roomId);

    final String fromUser = (from == null ? null : from.trim());
    requirePlayer(room, fromUser);

    final String opp = opponentOf(room, fromUser);
    if (opp == null) {
        safeCallback(fromUser, cb -> cb.onRedoResult(roomId, false, "Không có đối thủ để đề nghị hòa."));
        return;
    }

    if (pendingRedo.get(roomId) != null) {
        safeCallback(fromUser, cb -> cb.onRedoResult(roomId, false, "Đang có đề nghị hòa chờ xử lý."));
        return;
    }

    pendingRedo.put(roomId, new PendingDecision(fromUser, opp));
    safeCallback(opp, cb -> cb.onRedoRequested(roomId, fromUser));
    safeCallback(fromUser, cb -> cb.onRedoResult(roomId, true, "Đã gửi đề nghị hòa. Chờ đối thủ phản hồi..."));
}


   @Override
public synchronized void respondRedo(String roomId, String responder, boolean accept) throws RemoteException {
    Room room = state.rooms.get(roomId);
    if (room == null) return;

    final String responderUser = (responder == null ? null : responder.trim());
    requirePlayer(room, responderUser);

    final PendingDecision pending = pendingRedo.get(roomId);
    if (pending == null) {
        safeCallback(responderUser, cb -> cb.onRedoResult(roomId, false, "Không có yêu cầu Redo nào để phản hồi."));
        return;
    }

    if (!Objects.equals(pending.to, responderUser)) {
        safeCallback(responderUser, cb -> cb.onRedoResult(roomId, false, "Bạn không phải người nhận yêu cầu Redo này."));
        return;
    }

    final String requester = pending.from;
    pendingRedo.remove(roomId);

    if (!accept) {
        safeCallback(requester, cb -> cb.onRedoResult(roomId, false, responderUser + " đã từ chối Redo."));
        safeCallback(responderUser, cb -> cb.onRedoResult(roomId, true, "Bạn đã từ chối Redo."));
        return;
    }

        // Accept => kết thúc ván hòa
        for (String u : new ArrayList<>(room.players)) {
            safeCallback(u, cb -> cb.onAnnouncement("Hai bên đồng ý hòa ván."));
        }
        endGame(room, null, GameEndReason.DRAW);
        broadcastRooms();
    }

    // ============================================================
    // Post-game choice: REMATCH / RETURN
    // ============================================================
 @Override
public synchronized void submitPostGameChoice(String roomId, String username, Enums.PostGameChoice choice)
        throws RemoteException {

    Room room = state.rooms.get(roomId);
    if (room == null) throw new RemoteException("Room không tồn tại.");

    final String user = (username == null ? null : username.trim());

    // dùng helper hậu ván (tự re-attach nếu cần)
    requirePostGamePlayer(room, user);

    final String opp = opponentOf(room, user);

    // clear các pending kiểu Undo/Draw-offer vì đã qua ván
    pendingUndo.remove(roomId);
    pendingRedo.remove(roomId);
    pendingRematch.remove(roomId);

    // Lưu choice (KHÔNG remove map ngay tại đây)
    Map<String, PostGameChoice> choices = postGameChoices.computeIfAbsent(roomId, k -> new HashMap<>());
    choices.put(user, choice);

    // ===== 1) RETURN =====
    if (choice == Enums.PostGameChoice.RETURN) {
        // remove user khỏi phòng
        room.players.remove(user);

        // callback cho người rời
        safeCallback(user, cb -> cb.onReturnToLobby(roomId, "Bạn đã rời phòng. Trở về sảnh."));

        // dọn choice map vì đã có người rời
        postGameChoices.remove(roomId);

        // còn 1 người => giữ phòng WAITING, reset bàn để chờ người mới
        if (!room.players.isEmpty()) {
            String remaining = room.players.get(0);

            safeCallback(remaining, cb -> cb.onAnnouncement(user + " đã về lobby và không chơi nữa. Bạn đang chờ người khác vào..."));

            // nếu owner rời, chuyển owner
            if (Objects.equals(room.owner, user)) {
                room.owner = remaining;
            }

            resetRoomToWaiting(roomId, room);
            broadcastRooms();
            return;
        }

        // không còn ai => xóa phòng
        cleanupRoom(roomId);
        state.rooms.remove(roomId);
        broadcastRooms();
        return;
    }

    // ===== 2) REMATCH =====
    if (opp == null) {
        // chỉ còn 1 người trong room => không thể rematch
        safeCallback(user, cb -> cb.onAnnouncement("Không có đối thủ để rematch. Đang chờ người chơi khác vào..."));
        return;
    }

    PostGameChoice oppChoice = choices.get(opp);

    if (oppChoice == null) {
        // đối thủ chưa chọn => gửi request rematch cho họ
        safeCallback(opp, cb -> cb.onRematchRequested(roomId, user));
        safeCallback(user, cb -> cb.onAnnouncement("Đã gửi yêu cầu Rematch. Chờ đối thủ quyết định..."));
        return;
    }

    if (oppChoice == Enums.PostGameChoice.REMATCH) {
        // cả 2 đồng ý rematch => start match
        postGameChoices.remove(roomId);

        resetBoardAndSwap(room);
        resetDeadline(room);

        pushGameStart(room, room.playerX, Mark.X, true);
        pushGameStart(room, room.playerO, Mark.O, false);

        broadcastSnapshot(room);

        for (String u : new ArrayList<>(room.players)) {
            safeCallback(u, cb -> cb.onAnnouncement("Rematch bắt đầu. X đi trước."));
        }
        broadcastRooms();
        return;
    }

    // oppChoice == RETURN
    safeCallback(user, cb -> cb.onAnnouncement(opp + " đã chọn Return (từ chối Rematch)."));
}


   @Override
public synchronized void returnToLobby(String roomId, String from) throws RemoteException {
    Room room = state.rooms.get(roomId);
    if (room == null) return;

    final String actor = (from == null ? null : from.trim());
    requirePlayer(room, actor);

    // actor rời phòng
    room.players.remove(actor);

    // callback cho actor
    safeCallback(actor, cb -> cb.onReturnToLobby(roomId, "Bạn đã rời phòng. Trở về sảnh."));

    // nếu còn 1 người trong phòng -> thông báo + reset WAITING, không xóa phòng
    if (!room.players.isEmpty()) {
        String remaining = room.players.get(0);

        safeCallback(remaining, cb -> cb.onAnnouncement(actor + " đã về lobby. Bạn đang chờ người khác vào..."));
        safeCallback(remaining, cb -> cb.onGameEnded(new GameEnd(roomId, remaining, GameEndReason.ABORT)));

        if (Objects.equals(room.owner, actor)) room.owner = remaining;

        resetRoomToWaiting(roomId, room);
        broadcastRooms();
        return;
    }

    // không còn ai -> xóa phòng
    cleanupRoom(roomId);
    state.rooms.remove(roomId);
    broadcastRooms();
}

    // ============================================================
    // Ranking & friends
    // ============================================================
    @Override
    public List<UserProfile> getLeaderboard(int top) throws RemoteException {
        try {
            return state.userDao.topElo(top);
        } catch (SQLException e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void sendFriendRequest(FriendRequest req) throws RemoteException {
        try {
            state.userDao.ensureUser(req.getTo(), "123");
            state.friendDao.addFriendRequest(req.getFrom(), req.getTo());
            safeCallback(req.getTo(), cb -> cb.onFriendRequest(req));
        } catch (SQLException e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void respondFriendRequest(String from, String to, boolean accept) throws RemoteException {
        try {
            state.friendDao.resolveLatestPending(from, to, accept);
            if (accept) state.friendDao.addFriendPair(from, to);

            safeCallback(from, cb -> cb.onFriendListUpdated(state.friendDao.listFriends(from)));
            safeCallback(to, cb -> cb.onFriendListUpdated(state.friendDao.listFriends(to)));
        } catch (SQLException e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getFriends(String username) throws RemoteException {
        try {
            return state.friendDao.listFriends(username);
        } catch (SQLException e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listOnlineUsers() throws RemoteException {
        return sortedOnlineUsers();
    }

    // ============================================================
    // Internal helpers
    // ============================================================
    private void resetDeadline(Room room) {
        if (room.timed) {
            room.turnDeadlineMillis = System.currentTimeMillis() + room.timeLimitSeconds * 1000L;
        } else {
            room.turnDeadlineMillis = 0L;
        }
    }

    private void resetBoardAndSwap(Room room) {
        String oldX = room.playerX;
        room.playerX = room.playerO;
        room.playerO = oldX;

        room.turn = room.playerX;
        room.moveNo = 0;
        room.status = RoomStatus.PLAYING;

        clearBoard(room);

        moveHistory.computeIfAbsent(room.id, k -> new ArrayDeque<>()).clear();
        redoStack.computeIfAbsent(room.id, k -> new ArrayDeque<>()).clear();
        pendingUndo.remove(room.id);
        pendingRedo.remove(room.id);
    }

    private void clearBoard(Room room) {
        for (int r = 0; r < room.boardSize; r++) {
            for (int c = 0; c < room.boardSize; c++) {
                room.board[r][c] = Mark.EMPTY;
            }
        }
    }

   private void endGame(Room room, String winner, GameEndReason reason) throws RemoteException {
    try {
        postGameChoices.remove(room.id);

        // stop timer
        room.turnDeadlineMillis = 0L;

        GameEnd end = new GameEnd(room.id, winner, reason);
        for (String u : new ArrayList<>(room.players)) safeCallback(u, cb -> cb.onGameEnded(end));

        String x = room.playerX;
        String o = room.playerO;
        state.matchDao.insertMatch(room.id, x, o, winner, reason.name());

        if (reason == GameEndReason.WIN || reason == GameEndReason.RESIGN) {
            String loser = (winner == null) ? null : (winner.equals(x) ? o : x);
            applyEloAndStats(winner, loser, false);
        } else if (reason == GameEndReason.DRAW) {
            applyEloAndStats(x, o, true);
        }

        room.status = RoomStatus.WAITING;

        // IMPORTANT: kết thúc ván => không còn lượt
        room.turn = null;

        broadcastSnapshot(room);
        broadcastLeaderboard();
    } catch (SQLException e) {
        throw new RemoteException("DB error (endGame): " + e.getMessage(), e);
    }
}


    private void applyEloAndStats(String a, String b, boolean draw) throws SQLException {
        if (a == null || b == null) return;

        var ra = state.userDao.find(a).orElseThrow();
        var rb = state.userDao.find(b).orElseThrow();

        int aw = ra.wins, al = ra.losses, ad = ra.draws, ae = ra.elo;
        int bw = rb.wins, bl = rb.losses, bd = rb.draws, be = rb.elo;

        if (draw) {
            ad++;
            bd++;
        } else {
            aw++;
            bl++;
            ae += 20;
            be = Math.max(100, be - 20);
        }

        state.userDao.updateStats(a, aw, al, ad, ae);
        state.userDao.updateStats(b, bw, bl, bd, be);
    }

    private void cleanupRoom(String roomId) {
        pendingUndo.remove(roomId);
        pendingRedo.remove(roomId);
        pendingRematch.remove(roomId);
        postGameChoices.remove(roomId);

        moveHistory.remove(roomId);
        redoStack.remove(roomId);
    }

    private void requireOnline(String username) throws RemoteException {
        if (!state.online.containsKey(username)) throw new RemoteException("Chưa đăng nhập.");
    }

    private Room mustPlayingRoom(String roomId) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) throw new RemoteException("Room không tồn tại.");
        if (room.status != RoomStatus.PLAYING)
            throw new RemoteException("Room không ở trạng thái PLAYING.");
        return room;
    }
    private void requirePostGamePlayer(Room room, String user) throws RemoteException {
    if (user == null || user.trim().isEmpty()) throw new RemoteException("User không hợp lệ.");
    user = user.trim();

    if (room.players.contains(user)) return;

    // Nếu user vừa là người chơi của ván trước (playerX/playerO) và phòng còn chỗ
    boolean wasParticipant = Objects.equals(room.playerX, user) || Objects.equals(room.playerO, user);

    if (wasParticipant && room.status == RoomStatus.WAITING && room.players.size() < 2) {
        room.players.add(user); // re-attach để xử lý rematch hậu ván
        return;
    }

    throw new RemoteException("Bạn không ở trong phòng.");
}


    private void requirePlayer(Room room, String user) throws RemoteException {
        if (user == null || user.trim().isEmpty()) throw new RemoteException("User không hợp lệ.");
        user = user.trim();
        if (!room.players.contains(user)) throw new RemoteException("Bạn không ở trong phòng.");
    }

    private String opponentOf(Room room, String user) {
        for (String p : room.players) if (!Objects.equals(p, user)) return p;
        return null;
    }

    private boolean inBounds(Room room, int r, int c) {
        return r >= 0 && c >= 0 && r < room.boardSize && c < room.boardSize;
    }

    private boolean isWin(Room room, int row, int col, Mark mark) {
        return checkWin5(room, row, col, mark, room.blockTwoEnds);
    }

    // blockTwoEnds=true: cần ít nhất 1 đầu "mở"; biên được coi là mở
    private boolean checkWin5(Room room, int row, int col, Mark mark, boolean blockTwoEnds) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            int openEnds = 0;

            int r = row + d[0], c = col + d[1];
            while (inBounds(room, r, c) && room.board[r][c] == mark) {
                count++;
                r += d[0];
                c += d[1];
            }
            if (!inBounds(room, r, c) || room.board[r][c] == Mark.EMPTY) openEnds++;

            r = row - d[0];
            c = col - d[1];
            while (inBounds(room, r, c) && room.board[r][c] == mark) {
                count++;
                r -= d[0];
                c -= d[1];
            }
            if (!inBounds(room, r, c) || room.board[r][c] == Mark.EMPTY) openEnds++;

            if (count >= 5) {
                if (!blockTwoEnds) return true;
                return openEnds >= 1;
            }
        }
        return false;
    }

    private void pushGameStart(Room room, String user, Mark mark, boolean yourTurn) {
        String opp = room.players.stream().filter(p -> !p.equals(user)).findFirst().orElse("?");

        safeCallback(user, cb -> cb.onGameStarted(
                new GameStart(
                        room.id,
                        opp,
                        mark,
                        yourTurn,
                        room.boardSize,
                        room.blockTwoEnds,
                        room.timed,
                        room.timeLimitSeconds
                )
        ));
    }

    private RoomInfo toInfo(Room r) {
        return new RoomInfo(
                r.id, r.name, r.owner,
                2, r.players.size(), r.status, r.createdAt,
                r.boardSize, r.blockTwoEnds,
                r.hasPassword, r.timed, r.timeLimitSeconds
        );
    }

    private List<RoomInfo> allRoomsInfo() {
        return state.rooms.values().stream()
                .filter(r -> r.status != RoomStatus.CLOSED)
                .map(this::toInfo)
                .sorted(Comparator.comparing(RoomInfo::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    private List<String> sortedOnlineUsers() {
        List<String> users = new ArrayList<>(state.onlineUsers());
        users.sort(String::compareToIgnoreCase);
        return users;
    }

    private void broadcastRooms() {
        List<RoomInfo> rooms = allRoomsInfo();
        for (String u : sortedOnlineUsers()) safeCallback(u, cb -> cb.onRoomListUpdated(rooms));
    }

    private void broadcastOnlineUsers() {
        List<String> online = sortedOnlineUsers();
        for (String u : online) safeCallback(u, cb -> cb.onOnlineUsersUpdated(online));
    }

    private void broadcastLeaderboard() {
        try {
            List<UserProfile> lb = state.userDao.topElo(50);
            for (String u : sortedOnlineUsers()) safeCallback(u, cb -> cb.onLeaderboardUpdated(lb));
        } catch (SQLException ignored) {
        }
    }



//KHONG DUOC SUA HAM QUYET DINH CO BI DUNG TRONG ROOM GAME HAY KHONGGGGGGGGGG===============================================
    private void safeCallback(String user, CallbackCall call) {
        var s = state.online.get(user);
        if (s == null || s.callback == null) return;

        // FIX: Đưa việc gọi Client vào executor để chạy bất đồng bộ
        // Server sẽ không chờ Client trả lời nữa -> Tránh Deadlock
        callbackExecutor.submit(() -> {
            try {
                call.run(s.callback);
            } catch (Exception e) {
                System.err.println("[Callback error] user=" + user + " - " + e.getMessage());
                // Tùy chọn: Nếu lỗi kết nối quá nhiều, có thể xem xét remove user khỏi online
            }
        });
    }

    // ============================================================
    // Snapshot helpers
    // ============================================================
    private Mark[][] copyBoard(Room room) {
        Mark[][] b = new Mark[room.boardSize][room.boardSize];
        for (int r = 0; r < room.boardSize; r++) {
            System.arraycopy(room.board[r], 0, b[r], 0, room.boardSize);
        }
        return b;
    }

    private void broadcastSnapshot(Room room) {
        GameSnapshot snap = new GameSnapshot();
        snap.setRoomId(room.id);
        snap.setBoardSize(room.boardSize);
        snap.setBoard(copyBoard(room));
        snap.setMoveNo(room.moveNo);
        snap.setTurn(room.turn);

        snap.setTimed(room.timed);
        snap.setTimeLimitSeconds(room.timeLimitSeconds);
        snap.setTurnDeadlineMillis(room.timed ? room.turnDeadlineMillis : 0L);

        for (String u : new ArrayList<>(room.players)) {
            safeCallback(u, cb -> cb.onBoardReset(snap));
        }
    }

    // ============================================================
    // Undo core logic
    // ============================================================
    private UndoPlan computeUndoPlan(String roomId, Room room, String requester, String opponent) {
        Deque<MoveRecord> hist = moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>());
        if (hist.isEmpty()) return UndoPlan.no("Chưa có nước đi để Undo.");

        MoveRecord last = hist.peek(); // newest
        if (last == null) return UndoPlan.no("Lịch sử không hợp lệ.");

        // Case A: requester vừa đánh xong, đối thủ chưa đánh (last.by == requester)
        // -> xóa 1 nước của requester, trả lượt requester
        if (Objects.equals(last.by, requester)) {
            return UndoPlan.ok(1);
        }

        // Case B: đối thủ vừa đánh xong, tới lượt requester (last.by == opponent)
        // -> cần có nước trước đó là requester để "quay lại nước cuối của requester"
        if (Objects.equals(last.by, opponent)) {
            if (hist.size() < 2) return UndoPlan.no("Không đủ lịch sử để Undo.");
            Iterator<MoveRecord> it = hist.iterator();
            MoveRecord m1 = it.next(); // last (opponent)
            MoveRecord m2 = it.next(); // previous
            if (m2 == null) return UndoPlan.no("Không đủ lịch sử để Undo.");
            if (!Objects.equals(m2.by, requester)) {
                return UndoPlan.no("Không thể Undo: nước trước đó không phải của bạn.");
            }
            return UndoPlan.ok(2);
        }

        return UndoPlan.no("Không thể Undo: lịch sử nước đi không đúng trạng thái.");
    }

    private List<MoveRecord> applyUndoRollback(String roomId, Room room, String requester, int rollbackCount)
            throws RemoteException {

        Deque<MoveRecord> hist = moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>());
        if (hist.size() < rollbackCount) throw new RemoteException("Không đủ lịch sử để rollback.");

        List<MoveRecord> removed = new ArrayList<>(rollbackCount);

        for (int i = 0; i < rollbackCount; i++) {
            MoveRecord mv = hist.pop();
            removed.add(mv);
            if (inBounds(room, mv.row, mv.col)) {
                room.board[mv.row][mv.col] = Mark.EMPTY;
            }
        }

        room.moveNo = hist.isEmpty() ? 0 : hist.peek().moveNo;
        room.turn = requester;
        resetDeadline(room);

        // clear legacy redo stack (không dùng nữa)
        redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();

        broadcastSnapshot(room);
        return removed;
    }

    // ============================================================
    // Types
    // ============================================================
    @FunctionalInterface
    private interface CallbackCall {
        void run(ClientCallback cb) throws Exception;
    }

    private static class PendingDecision {
        final String from;
        final String to;

        PendingDecision(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class MoveRecord {
        final int row;
        final int col;
        final Mark mark;
        final String by;
        final int moveNo;

        MoveRecord(int row, int col, Mark mark, String by, int moveNo) {
            this.row = row;
            this.col = col;
            this.mark = mark;
            this.by = by;
            this.moveNo = moveNo;
        }
    }

    private static class UndoPlan {
        final boolean allowed;
        final int rollbackCount;
        final String reason;

        private UndoPlan(boolean allowed, int rollbackCount, String reason) {
            this.allowed = allowed;
            this.rollbackCount = rollbackCount;
            this.reason = reason;
        }

        static UndoPlan ok(int rollbackCount) {
            return new UndoPlan(true, rollbackCount, null);
        }

        static UndoPlan no(String reason) {
            return new UndoPlan(false, 0, reason);
        }
    }

@Override
public synchronized UserPublicProfile getUserPublicProfile(String requester, String target) throws RemoteException {
    if (requester == null || requester.isBlank()) throw new RemoteException("Requester invalid");
    if (target == null || target.isBlank()) throw new RemoteException("Target invalid");

    try {
        requester = requester.trim();
        target = target.trim();

        UserDao.UserRecord u = state.userDao.findByUsername(target);
        if (u == null) throw new RemoteException("User không tồn tại: " + target);

        int wins = u.wins, losses = u.losses, draws = u.draws, elo = u.elo;
        int games = wins + losses + draws;
        double wr = (games == 0) ? 0.0 : (wins * 100.0 / games);

        UserPublicProfile p = new UserPublicProfile();
        p.setUsername(u.username);
        p.setWins(wins);
        p.setLosses(losses);
        p.setDraws(draws);
        p.setElo(elo);
        p.setGamesPlayed(games);
        p.setWinRate(wr);

        p.setRank(computeRankByElo(elo, u.username));
        p.setFriendStatus(computeFriendStatus(requester, target));

        return p;

    } catch (RemoteException e) {
        throw e;
    } catch (Exception e) {
        throw new RemoteException("getUserPublicProfile failed: " + e.getMessage(), e);
    }
}


@Override
public synchronized boolean sendFriendRequestByName(String from, String to) throws RemoteException {
    if (from == null || from.isBlank()) throw new RemoteException("From invalid");
    if (to == null || to.isBlank()) throw new RemoteException("To invalid");

    from = from.trim();
    to = to.trim();

    if (from.equalsIgnoreCase(to)) throw new RemoteException("Không thể tự kết bạn với chính mình.");

    // ✅ tạo biến final để dùng trong lambda
    final String fromUser = from;
    final String toUser = to;

    try {
        if (!state.userDao.exists(toUser)) throw new RemoteException("User không tồn tại: " + toUser);

        if (state.friendDao.areFriends(fromUser, toUser)) {
            throw new RemoteException("Hai bạn đã là bạn bè.");
        }
        if (state.friendDao.hasPendingRequest(fromUser, toUser)) {
            throw new RemoteException("Bạn đã gửi lời mời trước đó.");
        }
        if (state.friendDao.hasPendingRequest(toUser, fromUser)) {
            throw new RemoteException("Đối thủ đã gửi lời mời cho bạn. Vui lòng vào mục Lời mời để chấp nhận.");
        }

        state.friendDao.createFriendRequest(fromUser, toUser);

        // ✅ dùng biến final trong lambda
        safeCallback(toUser, cb -> cb.onFriendRequest(new FriendRequest(fromUser, toUser, Instant.now())));

        return true;

    } catch (RemoteException e) {
        throw e;
    } catch (Exception e) {
        throw new RemoteException("sendFriendRequest failed: " + e.getMessage(), e);
    }
}

private int computeRankByElo(int elo, String username) throws SQLException {
    int higher = state.userDao.countUsersHigherElo(elo);
    return higher + 1;
}

private FriendStatus computeFriendStatus(String requester, String target) throws SQLException {
    if (state.friendDao.areFriends(requester, target)) return FriendStatus.FRIEND;
    if (state.friendDao.hasPendingRequest(requester, target)) return FriendStatus.OUTGOING_PENDING;
    if (state.friendDao.hasPendingRequest(target, requester)) return FriendStatus.INCOMING_PENDING;
    return FriendStatus.NOT_FRIEND;
}

}

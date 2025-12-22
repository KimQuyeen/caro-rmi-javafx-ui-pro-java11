package vn.edu.demo.caro.server.service;

import vn.edu.demo.caro.common.model.*;
import vn.edu.demo.caro.common.model.Enums.GameEndReason;
import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.model.Enums.PostGameChoice;
import vn.edu.demo.caro.common.model.Enums.RoomStatus;
import vn.edu.demo.caro.common.rmi.ClientCallback;
import vn.edu.demo.caro.common.rmi.LobbyService;
import vn.edu.demo.caro.server.state.Room;
import vn.edu.demo.caro.server.state.ServerState;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class LobbyServiceImpl extends UnicastRemoteObject implements LobbyService {

    private final ServerState state;

    // giữ để không vỡ client cũ (nhưng undo/redo mới không cần approval)
    private final Map<String, PendingDecision> pendingUndo = new HashMap<>();
    private final Map<String, PendingDecision> pendingRedo = new HashMap<>();
    private final Map<String, PendingDecision> pendingRematch = new HashMap<>();

    private final Map<String, Deque<MoveRecord>> moveHistory = new HashMap<>();
    private final Map<String, Deque<MoveRecord>> redoStack = new HashMap<>();

    // post-game choice: roomId -> (username -> choice)
    private final Map<String, Map<String, PostGameChoice>> postGameChoices = new HashMap<>();

    public LobbyServiceImpl(ServerState state) throws RemoteException {
        super(0);
        this.state = state;
    }

    // ---------------- Rematch / Return to lobby (legacy API) ----------------
@Override
public synchronized void requestRematch(String roomId, String from) throws RemoteException {
    // Legacy: "yêu cầu rematch" -> chuyển thành chọn REMATCH trong flow mới
    submitPostGameChoice(roomId, from, Enums.PostGameChoice.REMATCH);
}

@Override
public synchronized void respondRematch(String roomId, String responder, boolean accept) throws RemoteException {
    // Legacy: accept=true => REMATCH, accept=false => RETURN
    submitPostGameChoice(
            roomId,
            responder,
            accept ? Enums.PostGameChoice.REMATCH : Enums.PostGameChoice.RETURN
    );
}


    // ---------------- Auth ----------------
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

    // ---------------- Rooms ----------------
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

            // gửi gameStart trước để client setup UI
            pushGameStart(room, room.playerX, Mark.X, true);
            pushGameStart(room, room.playerO, Mark.O, false);

            // rồi snapshot để sync board + timer
            broadcastSnapshot(room);
        }

        broadcastRooms();
        return true;
    }

    @Override
    public synchronized void leaveRoom(String username, String roomId) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) return;

        username = (username == null ? null : username.trim());
        room.players.remove(username);

        if (room.status == RoomStatus.PLAYING && room.players.size() == 1) {
            String remaining = room.players.get(0);
            safeCallback(remaining, cb -> cb.onGameEnded(new GameEnd(roomId, remaining, GameEndReason.ABORT)));

            cleanupRoom(roomId);
            state.rooms.remove(roomId);

        } else if (room.players.isEmpty()) {
            cleanupRoom(roomId);
            state.rooms.remove(roomId);

        } else {
            room.status = RoomStatus.WAITING;
        }

        broadcastRooms();
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

    // ---------------- Chat ----------------
    @Override
    public void sendGlobalChat(ChatMessage msg) throws RemoteException {
        for (String u : sortedOnlineUsers()) safeCallback(u, cb -> cb.onGlobalChat(msg));
    }

    @Override
    public void sendRoomChat(String roomId, ChatMessage msg) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) throw new RemoteException("Room không tồn tại.");
        for (String u : new ArrayList<>(room.players)) safeCallback(u, cb -> cb.onRoomChat(roomId, msg));
    }

    // ---------------- Gameplay ----------------
    @Override
    public synchronized void makeMove(String roomId, String username, int row, int col) throws RemoteException {
        Room room = state.rooms.get(roomId);
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

        // new move => clear redo
        redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();

        Mark mark = username.equals(room.playerX) ? Mark.X : Mark.O;
        room.board[row][col] = mark;

        int moveNo = ++room.moveNo;

        moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>())
                .push(new MoveRecord(row, col, mark, username, moveNo));

        String next = username.equals(room.playerX) ? room.playerO : room.playerX;
        room.turn = next;
        resetDeadline(room);

        // gửi update trước để client vẽ nhanh
        Move mv = new Move(row, col, moveNo, username);
        GameUpdate update = new GameUpdate(roomId, mv, mark, next);
        for (String u : new ArrayList<>(room.players)) safeCallback(u, cb -> cb.onGameUpdated(update));

        // snapshot để sync turn+timer+board authoritative
        broadcastSnapshot(room);

        // check win/draw
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

    // ---------------- Undo/Redo: CHỈ NƯỚC CỦA MÌNH ----------------
    @Override
    public synchronized void requestUndo(String roomId, String from) throws RemoteException {
        Room room = mustPlayingRoom(roomId);
        requirePlayer(room, from);
        from = from.trim();

        Deque<MoveRecord> hist = moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>());
        if (hist.isEmpty()) {
            safeCallback(from, cb -> cb.onUndoResult(roomId, false, "Chưa có nước đi để Undo."));
            return;
        }

        MoveRecord last = hist.peek();
        if (last == null) {
            safeCallback(from, cb -> cb.onUndoResult(roomId, false, "Chưa có nước đi để Undo."));
            return;
        }

        // chỉ undo nếu nước cuối là của mình
        if (!Objects.equals(last.by, from)) {
            safeCallback(from, cb -> cb.onUndoResult(roomId, false,
                    "Bạn chỉ được Undo nước mới nhất của chính bạn."));
            return;
        }

        // chỉ undo khi sau đó tới lượt đối thủ (bạn vừa đánh xong)
        if (Objects.equals(room.turn, from)) {
            safeCallback(from, cb -> cb.onUndoResult(roomId, false,
                    "Không thể Undo lúc đang tới lượt bạn."));
            return;
        }

        hist.pop();
        redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).push(last);

        room.board[last.row][last.col] = Mark.EMPTY;
        room.moveNo = Math.max(0, room.moveNo - 1);

        // trả lượt về cho người undo
        room.turn = from;
        resetDeadline(room);

        broadcastSnapshot(room);

        for (String u : new ArrayList<>(room.players)) {
            safeCallback(u, cb -> cb.onUndoResult(roomId, true,
                    "Undo: (" + last.row + "," + last.col + "). Lượt: " + room.turn));
        }
    }

    @Override
    public synchronized void requestRedo(String roomId, String from) throws RemoteException {
        Room room = mustPlayingRoom(roomId);
        requirePlayer(room, from);
        from = from.trim();

        Deque<MoveRecord> redo = redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>());
        if (redo.isEmpty()) {
            safeCallback(from, cb -> cb.onRedoResult(roomId, false, "Không có nước để Redo."));
            return;
        }

        MoveRecord mv = redo.peek();
        if (mv == null) {
            safeCallback(from, cb -> cb.onRedoResult(roomId, false, "Không có nước để Redo."));
            return;
        }

        // chỉ redo nước của mình
        if (!Objects.equals(mv.by, from)) {
            safeCallback(from, cb -> cb.onRedoResult(roomId, false,
                    "Bạn chỉ được Redo nước của chính bạn."));
            return;
        }

        // redo chỉ khi tới lượt bạn
        if (!Objects.equals(room.turn, from)) {
            safeCallback(from, cb -> cb.onRedoResult(roomId, false,
                    "Chỉ được Redo khi đang tới lượt bạn."));
            return;
        }

        redo.pop();

        if (!inBounds(room, mv.row, mv.col) || room.board[mv.row][mv.col] != Mark.EMPTY) {
            safeCallback(from, cb -> cb.onRedoResult(roomId, false,
                    "Không thể Redo vì ô không hợp lệ / đã có quân."));
            return;
        }

        room.board[mv.row][mv.col] = mv.mark;
        int newNo = ++room.moveNo;

        // push record mới với moveNo mới
        moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>())
                .push(new MoveRecord(mv.row, mv.col, mv.mark, mv.by, newNo));

        // sau redo -> tới lượt đối thủ
        String opp = opponentOf(room, from);
        if (opp != null) room.turn = opp;

        resetDeadline(room);
        broadcastSnapshot(room);

        for (String u : new ArrayList<>(room.players)) {
            safeCallback(u, cb -> cb.onRedoResult(roomId, true,
                    "Redo: (" + mv.row + "," + mv.col + "). Lượt: " + room.turn));
        }
    }

    // giữ cho client cũ không chết (nhưng không dùng)
    @Override public synchronized void respondUndo(String roomId, String responder, boolean accept) throws RemoteException {
        safeCallback(responder, cb -> cb.onUndoResult(roomId, false, "Undo không cần đối thủ đồng ý."));
    }
    @Override public synchronized void respondRedo(String roomId, String responder, boolean accept) throws RemoteException {
        safeCallback(responder, cb -> cb.onRedoResult(roomId, false, "Redo không cần đối thủ đồng ý."));
    }

    // ---------------- Post-game choice: REMATCH / RETURN ----------------
@Override
public synchronized void submitPostGameChoice(String roomId, String username, Enums.PostGameChoice choice)
        throws RemoteException {

    Room room = state.rooms.get(roomId);
    if (room == null) throw new RemoteException("Room không tồn tại.");

    final String user = (username == null ? null : username.trim());
    requirePlayer(room, user);

    postGameChoices.computeIfAbsent(roomId, k -> new HashMap<>()).put(user, choice);
    final String opp = opponentOf(room, user);

    // 1) RETURN => cả 2 về lobby
    if (choice == Enums.PostGameChoice.RETURN) {
        if (opp != null) {
            safeCallback(opp, cb -> cb.onAnnouncement(user + " đã chọn Return to lobby."));
        }
        for (String u : new ArrayList<>(room.players)) {
            safeCallback(u, cb -> cb.onReturnToLobby(roomId, "Trận kết thúc. Trở về sảnh."));
        }
        cleanupRoom(roomId);
        state.rooms.remove(roomId);
        broadcastRooms();
        return;
    }

    // 2) REMATCH
    if (opp == null) {
        safeCallback(user, cb -> cb.onAnnouncement("Đã chọn Rematch. Chờ đối thủ..."));
        return;
    }

    // báo đối thủ biết bạn đã chọn rematch
    safeCallback(opp, cb -> cb.onAnnouncement(user + " đã chọn Rematch. Bạn chọn Rematch hoặc Return."));

    Enums.PostGameChoice oppChoice = postGameChoices.get(roomId).get(opp);

    // nếu đối thủ cũng chọn rematch => bắt đầu ván mới
    if (oppChoice == Enums.PostGameChoice.REMATCH) {
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
    } else {
        safeCallback(user, cb -> cb.onAnnouncement("Đã chọn Rematch. Chờ đối thủ quyết định..."));
    }
}


    @Override
public synchronized void returnToLobby(String roomId, String from) throws RemoteException {
    Room room = state.rooms.get(roomId);
    if (room == null) return;

    final String actor = (from == null ? null : from.trim());
    requirePlayer(room, actor);

    final String opp = opponentOf(room, actor);
    if (opp != null) {
        safeCallback(opp, cb -> cb.onAnnouncement(actor + " đã chọn Return to lobby."));
        if (room.status == RoomStatus.PLAYING) {
            safeCallback(opp, cb -> cb.onGameEnded(new GameEnd(roomId, opp, GameEndReason.ABORT)));
        }
    }

    for (String u : new ArrayList<>(room.players)) {
        safeCallback(u, cb -> cb.onReturnToLobby(roomId, "Trở về sảnh."));
    }

    cleanupRoom(roomId);
    state.rooms.remove(roomId);
    broadcastRooms();
}


    // ---------------- Ranking & friends ----------------
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

    // ---------------- Internal helpers ----------------
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

            // stop timer snapshot (optional nhưng nên)
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

            // gửi snapshot để client dừng timer, đồng bộ final state
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
            ad++; bd++;
        } else {
            aw++; bl++;
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
        int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int count = 1;
            int openEnds = 0;

            int r = row + d[0], c = col + d[1];
            while (inBounds(room, r, c) && room.board[r][c] == mark) {
                count++;
                r += d[0]; c += d[1];
            }
            if (!inBounds(room, r, c) || room.board[r][c] == Mark.EMPTY) openEnds++;

            r = row - d[0]; c = col - d[1];
            while (inBounds(room, r, c) && room.board[r][c] == mark) {
                count++;
                r -= d[0]; c -= d[1];
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
        } catch (SQLException ignored) {}
    }

    private void safeCallback(String user, CallbackCall call) {
        var s = state.online.get(user);
        if (s == null || s.callback == null) return;
        try { call.run(s.callback); } catch (Exception ignored) {}
    }

    // ===== Snapshot helpers =====
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

   

    @FunctionalInterface
    private interface CallbackCall { void run(ClientCallback cb) throws Exception; }

    private static class PendingDecision {
        final String from;
        final String to;
        PendingDecision(String from, String to) { this.from = from; this.to = to; }
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
}

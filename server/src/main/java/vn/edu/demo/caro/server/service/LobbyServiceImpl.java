package vn.edu.demo.caro.server.service;

import vn.edu.demo.caro.common.model.*;
import vn.edu.demo.caro.common.model.Enums.GameEndReason;
import vn.edu.demo.caro.common.model.Enums.Mark;
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

    private final Map<String, PendingDecision> pendingUndo = new HashMap<>();
    private final Map<String, PendingDecision> pendingRedo = new HashMap<>();
    private final Map<String, PendingDecision> pendingRematch = new HashMap<>();

    private final Map<String, Deque<MoveRecord>> moveHistory = new HashMap<>();
    private final Map<String, Deque<MoveRecord>> redoStack = new HashMap<>();

    public LobbyServiceImpl(ServerState state) throws RemoteException {
        super(0);
        this.state = state;
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
        Room room = state.rooms.get(roomId);
        if (room == null) throw new RemoteException("Room không tồn tại.");

        if (room.status != RoomStatus.WAITING) return false;
        if (room.isFull()) return false;

        if (room.hasPassword) {
            if (password == null || password.trim().isEmpty()) {
                throw new RemoteException("Phòng yêu cầu mật khẩu.");
            }
            if (!Objects.equals(room.password, password)) {
                throw new RemoteException("Mật khẩu phòng không đúng.");
            }
        }

        if (!room.players.contains(username)) room.players.add(username);

        if (room.players.size() == 2) {
            // ===== start match (RESET STATE AUTHORITATIVE) =====
            room.status = RoomStatus.PLAYING;

            String p1 = room.players.get(0);
            String p2 = room.players.get(1);

            boolean p1IsX = ThreadLocalRandom.current().nextBoolean();
            room.playerX = p1IsX ? p1 : p2;
            room.playerO = p1IsX ? p2 : p1;
            room.turn = room.playerX;

            room.moveNo = 0;
            clearBoard(room);

            moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();
            redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();
            pendingUndo.remove(roomId);
            pendingRedo.remove(roomId);

            if (room.timed) {
                room.turnDeadlineMillis = System.currentTimeMillis() + room.timeLimitSeconds * 1000L;
            } else {
                room.turnDeadlineMillis = 0L;
            }

            pushGameStart(room, room.playerX, Mark.X, true);
            pushGameStart(room, room.playerO, Mark.O, false);

            // snapshot ngay khi bắt đầu để client chắc chắn đúng size/board
            broadcastSnapshot(room);
        }

        broadcastRooms();
        return true;
    }

    @Override
    public synchronized void leaveRoom(String username, String roomId) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) return;

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

    username = username.trim(); // QUAN TRỌNG: tránh lệch do khoảng trắng

    if (!room.players.contains(username)) throw new RemoteException("Bạn không ở trong phòng.");

    if (!Objects.equals(room.turn, username)) {
        throw new RemoteException("Chưa đến lượt bạn. Lượt hiện tại: " + room.turn);
    }
        if (!inBounds(room, row, col)) throw new RemoteException("Nước đi ngoài bàn cờ.");
        if (room.board[row][col] != Mark.EMPTY) throw new RemoteException("Ô đã được đánh.");

        // Clear redo stack when a new move is made
        // Clear redo stack when a new move is made
redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).clear();


        Mark mark = username.equals(room.playerX) ? Mark.X : Mark.O;
        room.board[row][col] = mark;

        int moveNo = ++room.moveNo;

        moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>())
                .push(new MoveRecord(row, col, mark, username, moveNo));

        String next = username.equals(room.playerX) ? room.playerO : room.playerX;
        room.turn = next;

        // reset deadline cho lượt tiếp theo (nếu timed)
        if (room.timed) {
            room.turnDeadlineMillis = System.currentTimeMillis() + room.timeLimitSeconds * 1000L;
        }

        Move mv = new Move(row, col, moveNo, username);
        GameUpdate update = new GameUpdate(roomId, mv, mark, next);

        for (String u : new ArrayList<>(room.players)) safeCallback(u, cb -> cb.onGameUpdated(update));

        // snapshot để client đồng bộ chắc chắn (khuyến nghị)
        broadcastSnapshot(room);

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

    // ---------------- Undo/Redo ----------------
    @Override
public synchronized void requestUndo(String roomId, String from) throws RemoteException {
    Room room = mustPlayingRoom(roomId);
    requirePlayer(room, from);

    Deque<MoveRecord> hist = moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>());
    if (hist.isEmpty()) {
        safeCallback(from, cb -> cb.onUndoResult(roomId, false, "Chưa có nước đi để Undo."));
        return;
    }

    // Rule: chỉ undo nếu nước mới nhất là của chính bạn
    MoveRecord last = hist.peek();
    if (!Objects.equals(last.by, from)) {
        safeCallback(from, cb -> cb.onUndoResult(roomId, false,
                "Bạn chỉ được Undo nước mới nhất của CHÍNH bạn (khi đối thủ chưa đi)."));
        return;
    }

    // Rule: sau khi bạn đánh, turn đang là đối thủ; nếu đang là lượt bạn thì không hợp lệ để undo “nước vừa đánh”
    if (Objects.equals(room.turn, from)) {
        safeCallback(from, cb -> cb.onUndoResult(roomId, false,
                "Không thể Undo lúc đang tới lượt bạn. Bạn chỉ Undo ngay sau khi vừa đánh xong."));
        return;
    }

    // Thực hiện undo
    hist.pop();
    Deque<MoveRecord> redo = redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>());
    redo.push(last);

    room.board[last.row][last.col] = Mark.EMPTY;
    room.moveNo = Math.max(0, room.moveNo - 1);

    // trả lượt về cho người vừa undo
    room.turn = from;

    // đồng bộ lại bàn cờ cho cả 2 client
    broadcastSnapshot(room);

    for (String u : new ArrayList<>(room.players)) {
        safeCallback(u, cb -> cb.onUndoResult(roomId, true,
                "Undo thành công: (" + last.row + "," + last.col + "). Lượt hiện tại: " + room.turn));
    }
}


    @Override
    public synchronized void respondUndo(String roomId, String responder, boolean accept) throws RemoteException {
        Room room = mustPlayingRoom(roomId);
        requirePlayer(room, responder);

        PendingDecision pd = pendingUndo.get(roomId);
        if (pd == null) throw new RemoteException("Không có yêu cầu Undo nào.");
        if (!pd.to.equals(responder)) throw new RemoteException("Bạn không phải người nhận yêu cầu Undo.");

        String requester = pd.from;
        pendingUndo.remove(roomId);

        if (!accept) {
            safeCallback(requester, cb -> cb.onUndoResult(roomId, false, responder + " đã từ chối Undo."));
            safeCallback(responder, cb -> cb.onUndoResult(roomId, false, "Bạn đã từ chối Undo."));
            return;
        }

        Deque<MoveRecord> hist = moveHistory.getOrDefault(roomId, new ArrayDeque<>());
        if (hist.isEmpty()) {
            safeCallback(requester, cb -> cb.onUndoResult(roomId, false, "Không có nước đi để Undo."));
            safeCallback(responder, cb -> cb.onUndoResult(roomId, false, "Không có nước đi để Undo."));
            return;
        }

        MoveRecord last = hist.pop();

        // push to redo stack
        redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>()).push(last);

        room.board[last.row][last.col] = Mark.EMPTY;
        room.moveNo = Math.max(0, room.moveNo - 1);

        // turn becomes the player who made the undone move
        room.turn = last.by;

        // reset deadline cho lượt hiện tại (nếu timed)
        if (room.timed) {
            room.turnDeadlineMillis = System.currentTimeMillis() + room.timeLimitSeconds * 1000L;
        }

        safeCallback(requester, cb -> cb.onUndoResult(roomId, true,
                "Undo thành công: (" + last.row + "," + last.col + "). Lượt: " + room.turn));
        safeCallback(responder, cb -> cb.onUndoResult(roomId, true,
                "Bạn đã đồng ý Undo. Lượt: " + room.turn));

        broadcastSnapshot(room);
    }

    @Override
public synchronized void requestRedo(String roomId, String from) throws RemoteException {
    Room room = mustPlayingRoom(roomId);
    requirePlayer(room, from);

    Deque<MoveRecord> redo = redoStack.computeIfAbsent(roomId, k -> new ArrayDeque<>());
    if (redo.isEmpty()) {
        safeCallback(from, cb -> cb.onRedoResult(roomId, false, "Không có nước để Redo."));
        return;
    }

    MoveRecord mv = redo.peek();

    // Rule: chỉ redo nước của chính bạn
    if (!Objects.equals(mv.by, from)) {
        safeCallback(from, cb -> cb.onRedoResult(roomId, false,
                "Bạn chỉ được Redo nước của CHÍNH bạn."));
        return;
    }

    // Rule: redo chỉ khi đang là lượt bạn (vì undo xong trả lượt về bạn)
    if (!Objects.equals(room.turn, from)) {
        safeCallback(from, cb -> cb.onRedoResult(roomId, false,
                "Chỉ được Redo khi đang tới lượt bạn."));
        return;
    }

    // apply redo
    redo.pop();

    if (!inBounds(room, mv.row, mv.col) || room.board[mv.row][mv.col] != Mark.EMPTY) {
        safeCallback(from, cb -> cb.onRedoResult(roomId, false,
                "Không thể Redo vì ô không hợp lệ / đã có quân."));
        return;
    }

    room.board[mv.row][mv.col] = mv.mark;
    room.moveNo++;

    moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>()).push(mv);

    // sau khi redo nước của bạn, tới lượt đối thủ
    String opp = opponentOf(room, from);
    room.turn = (opp == null ? room.turn : opp);

    broadcastSnapshot(room);

    for (String u : new ArrayList<>(room.players)) {
        safeCallback(u, cb -> cb.onRedoResult(roomId, true,
                "Redo thành công: (" + mv.row + "," + mv.col + "). Lượt hiện tại: " + room.turn));
    }}

    @Override
    public synchronized void respondRedo(String roomId, String responder, boolean accept) throws RemoteException {
        Room room = mustPlayingRoom(roomId);
        requirePlayer(room, responder);

        PendingDecision pd = pendingRedo.get(roomId);
        if (pd == null) throw new RemoteException("Không có yêu cầu Redo nào.");
        if (!pd.to.equals(responder)) throw new RemoteException("Bạn không phải người nhận yêu cầu Redo.");

        String requester = pd.from;
        pendingRedo.remove(roomId);

        if (!accept) {
            safeCallback(requester, cb -> cb.onRedoResult(roomId, false, responder + " đã từ chối Redo."));
            safeCallback(responder, cb -> cb.onRedoResult(roomId, false, "Bạn đã từ chối Redo."));
            return;
        }

        Deque<MoveRecord> redo = redoStack.getOrDefault(roomId, new ArrayDeque<>());
        if (redo.isEmpty()) {
            safeCallback(requester, cb -> cb.onRedoResult(roomId, false, "Không có nước để Redo."));
            safeCallback(responder, cb -> cb.onRedoResult(roomId, false, "Không có nước để Redo."));
            return;
        }

        MoveRecord mv = redo.pop();

        if (!inBounds(room, mv.row, mv.col) || room.board[mv.row][mv.col] != Mark.EMPTY) {
            safeCallback(requester, cb -> cb.onRedoResult(roomId, false, "Không thể Redo vì ô không hợp lệ."));
            safeCallback(responder, cb -> cb.onRedoResult(roomId, false, "Không thể Redo vì ô không hợp lệ."));
            return;
        }

        room.board[mv.row][mv.col] = mv.mark;
        int newNo = ++room.moveNo;

        // push history với moveNo mới (tránh lệch)
        moveHistory.computeIfAbsent(roomId, k -> new ArrayDeque<>())
                .push(new MoveRecord(mv.row, mv.col, mv.mark, mv.by, newNo));

        String next = mv.by.equals(room.playerX) ? room.playerO : room.playerX;
        room.turn = next;

        if (room.timed) {
            room.turnDeadlineMillis = System.currentTimeMillis() + room.timeLimitSeconds * 1000L;
        }

        safeCallback(requester, cb -> cb.onRedoResult(roomId, true,
                "Redo thành công: (" + mv.row + "," + mv.col + "). Lượt: " + room.turn));
        safeCallback(responder, cb -> cb.onRedoResult(roomId, true,
                "Bạn đã đồng ý Redo. Lượt: " + room.turn));

        broadcastSnapshot(room);
    }

    // ---------------- Rematch / Return ----------------
    @Override
    public synchronized void requestRematch(String roomId, String from) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) throw new RemoteException("Room không tồn tại.");
        requirePlayer(room, from);

        String opp = opponentOf(room, from);
        if (opp == null) throw new RemoteException("Chưa có đối thủ.");

        pendingRematch.put(roomId, new PendingDecision(from, opp));
        safeCallback(opp, cb -> cb.onAnnouncement("[REMATCH] " + from + " yêu cầu chơi lại. Đồng ý/Từ chối?"));
        safeCallback(from, cb -> cb.onAnnouncement("[REMATCH] Đã gửi yêu cầu rematch tới " + opp));
    }

    @Override
    public synchronized void respondRematch(String roomId, String responder, boolean accept) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) throw new RemoteException("Room không tồn tại.");
        requirePlayer(room, responder);

        PendingDecision pd = pendingRematch.get(roomId);
        if (pd == null) throw new RemoteException("Không có yêu cầu rematch.");
        if (!pd.to.equals(responder)) throw new RemoteException("Bạn không phải người nhận yêu cầu rematch.");

        String requester = pd.from;
        pendingRematch.remove(roomId);

        if (!accept) {
            safeCallback(requester, cb -> cb.onAnnouncement("[REMATCH] " + responder + " từ chối rematch."));
            safeCallback(responder, cb -> cb.onAnnouncement("[REMATCH] Bạn đã từ chối rematch."));
            return;
        }

        resetBoardAndSwap(room);

        pushGameStart(room, room.playerX, Mark.X, true);
        pushGameStart(room, room.playerO, Mark.O, false);

        // snapshot để client reset UI đúng board size + state
        broadcastSnapshot(room);

        for (String u : new ArrayList<>(room.players)) {
            safeCallback(u, cb -> cb.onAnnouncement("[REMATCH] Bắt đầu ván mới. X đi trước. Kích thước bàn=" + room.boardSize));
        }

        broadcastRooms();
    }

    @Override
    public synchronized void returnToLobby(String roomId, String from) throws RemoteException {
        Room room = state.rooms.get(roomId);
        if (room == null) return;

        requirePlayer(room, from);
        String opp = opponentOf(room, from);

        if (opp != null) {
            safeCallback(opp, cb -> cb.onAnnouncement("[ROOM] " + from + " đã rời về sảnh. Bạn sẽ được đưa về sảnh."));
            safeCallback(opp, cb -> cb.onGameEnded(new GameEnd(roomId, opp, GameEndReason.ABORT)));
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

        if (room.timed) {
            room.turnDeadlineMillis = System.currentTimeMillis() + room.timeLimitSeconds * 1000L;
        } else {
            room.turnDeadlineMillis = 0L;
        }
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
        if (user == null || user.isBlank()) throw new RemoteException("User không hợp lệ.");
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

   private boolean checkWin5(Room room, int row, int col, Mark mark, boolean blockTwoEnds) {
    int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}};
    for (int[] d : dirs) {
        int count = 1;
        int openEnds = 0;

        // forward
        int r = row + d[0], c = col + d[1];
        while (inBounds(room, r, c) && room.board[r][c] == mark) {
            count++;
            r += d[0]; c += d[1];
        }
        // sửa: ra khỏi bàn cũng tính là open end
        if (!inBounds(room, r, c) || room.board[r][c] == Mark.EMPTY) openEnds++;

        // backward
        r = row - d[0]; c = col - d[1];
        while (inBounds(room, r, c) && room.board[r][c] == mark) {
            count++;
            r -= d[0]; c -= d[1];
        }
        // sửa: ra khỏi bàn cũng tính là open end
        if (!inBounds(room, r, c) || room.board[r][c] == Mark.EMPTY) openEnds++;

        if (count >= 5) {
            if (!blockTwoEnds) return true;
            // blockTwoEnds=true: chỉ cần >=1 đầu mở
            return openEnds >= 1;
        }
    }
    return false;
}


    private void pushGameStart(Room room, String user, Mark mark, boolean yourTurn) {
        String opp = room.players.stream()
                .filter(p -> !p.equals(user))
                .findFirst()
                .orElse("?");

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

        System.out.println("[GameStart] to=" + user +
                " mark=" + mark + " yourTurn=" + yourTurn +
                " turn=" + room.turn + " X=" + room.playerX + " O=" + room.playerO);
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
        GameSnapshot snap = new GameSnapshot(
                room.id,
                room.boardSize,
                copyBoard(room),
                room.moveNo,
                room.turn
        );
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
    
    private String norm(String s) throws RemoteException {
    if (s == null) throw new RemoteException("User null.");
    s = s.trim();
    if (s.isEmpty()) throw new RemoteException("User rỗng.");
    return s;
}

}

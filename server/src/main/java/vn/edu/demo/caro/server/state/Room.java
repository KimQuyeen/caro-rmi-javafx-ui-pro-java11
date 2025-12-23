package vn.edu.demo.caro.server.state;

import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.model.Enums.RoomStatus;
import vn.edu.demo.caro.common.model.Move;
import vn.edu.demo.caro.common.model.RoomCreateRequest;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
public class Room {
    public final String id;
    public final String name;
public String owner;
    public final Instant createdAt = Instant.now();
    public volatile RoomStatus status = RoomStatus.WAITING;
    public final List<String> players = new ArrayList<>(2);

    public int boardSize;
    public boolean blockTwoEnds;

    public boolean hasPassword;
    public String password;

    public boolean timed;
    public int timeLimitSeconds;
    public long turnDeadlineMillis;

    public final Mark[][] board;
    public volatile String playerX;
    public volatile String playerO;
    public volatile String turn;
    public volatile int moveNo = 0;


    public String getOwner() { return owner; }
public void setOwner(String owner) { this.owner = owner; }
    // --- move history for undo/redo ---
public final List<Move> history = new ArrayList<>();
public final Deque<Move> redoStack = new ArrayDeque<>();

// --- request states ---
public volatile String pendingUndoFrom = null;   // username
public volatile String pendingRedoFrom = null;   // username
public volatile String pendingRematchFrom = null;

// --- rematch choice tracking ---
public volatile boolean xWantsRematch = false;
public volatile boolean oWantsRematch = false;

// helper: reset game board but keep room settings
public synchronized void resetGameKeepSettings(boolean swapFirstTurn) {
    for (int r = 0; r < boardSize; r++) {
        Arrays.fill(board[r], Mark.EMPTY);
    }
    moveNo = 0;
    history.clear();
    redoStack.clear();

    // swap who is X and who is O to swap first turn
    if (swapFirstTurn) {
        String tmp = playerX;
        playerX = playerO;
        playerO = tmp;
    }
    turn = playerX;
    turnDeadlineMillis = timed ? (System.currentTimeMillis() + timeLimitSeconds * 1000L) : 0L;

    pendingUndoFrom = null;
    pendingRedoFrom = null;
    pendingRematchFrom = null;
    xWantsRematch = false;
    oWantsRematch = false;
}


    public Room(String id, String owner, RoomCreateRequest req) {
        this.id = id;
        this.owner = owner;
        this.name = req.getRoomName();

        this.boardSize = req.getBoardSize();
        this.board = new Mark[boardSize][boardSize];
        for (int r = 0; r < boardSize; r++) {
            Arrays.fill(this.board[r], Mark.EMPTY);
        }

        this.blockTwoEnds = req.isBlockTwoEnds();

        this.hasPassword = req.isPasswordEnabled();
        this.password = this.hasPassword ? req.getPassword() : null;

        this.timed = req.isTimed();
        this.timeLimitSeconds = this.timed ? req.getTimeLimitSeconds() : 0;
        this.turnDeadlineMillis = 0L;

        players.add(owner);
    }

    public boolean isFull() { return players.size() >= 2; }
}

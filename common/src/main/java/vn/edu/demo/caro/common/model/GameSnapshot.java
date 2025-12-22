package vn.edu.demo.caro.common.model;

import vn.edu.demo.caro.common.model.Enums.Mark;

import java.io.Serializable;

public class GameSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private String roomId;
    private int boardSize;
    private Mark[][] board;
    private int moveNo;
    private String turn;

    // ===== Timer fields =====
    private boolean timed;
    private int timeLimitSeconds;
    private long turnDeadlineMillis;

    public GameSnapshot() {}

    public String getRoomId() { return roomId; }
    public int getBoardSize() { return boardSize; }
    public Mark[][] getBoard() { return board; }
    public int getMoveNo() { return moveNo; }
    public String getTurn() { return turn; }

    public boolean isTimed() { return timed; }
    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public long getTurnDeadlineMillis() { return turnDeadlineMillis; }

    public void setRoomId(String roomId) { this.roomId = roomId; }
    public void setBoardSize(int boardSize) { this.boardSize = boardSize; }
    public void setBoard(Mark[][] board) { this.board = board; }
    public void setMoveNo(int moveNo) { this.moveNo = moveNo; }
    public void setTurn(String turn) { this.turn = turn; }

    public void setTimed(boolean timed) { this.timed = timed; }
    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }
    public void setTurnDeadlineMillis(long turnDeadlineMillis) { this.turnDeadlineMillis = turnDeadlineMillis; }
}

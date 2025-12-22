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

    public GameSnapshot() { }

    public GameSnapshot(String roomId, int boardSize, Mark[][] board, int moveNo, String turn) {
        this.roomId = roomId;
        this.boardSize = boardSize;
        this.board = board;
        this.moveNo = moveNo;
        this.turn = turn;
    }

    public String getRoomId() { return roomId; }
    public int getBoardSize() { return boardSize; }
    public Mark[][] getBoard() { return board; }
    public int getMoveNo() { return moveNo; }
    public String getTurn() { return turn; }

    public void setRoomId(String roomId) { this.roomId = roomId; }
    public void setBoardSize(int boardSize) { this.boardSize = boardSize; }
    public void setBoard(Mark[][] board) { this.board = board; }
    public void setMoveNo(int moveNo) { this.moveNo = moveNo; }
    public void setTurn(String turn) { this.turn = turn; }
}

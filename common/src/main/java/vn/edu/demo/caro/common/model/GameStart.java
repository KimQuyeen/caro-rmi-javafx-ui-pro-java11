package vn.edu.demo.caro.common.model;

import vn.edu.demo.caro.common.model.Enums.Mark;

import java.io.Serializable;

public class GameStart implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String roomId;
    private final String opponent;
    private final Mark yourMark;
    private final boolean yourTurn;

    // --- room rules/options (so client can build board correctly) ---
    private final int boardSize;
    private final boolean blockTwoEnds;

    private final boolean timed;
    private final int timeLimitSeconds;

    public GameStart(String roomId,
                     String opponent,
                     Mark yourMark,
                     boolean yourTurn,
                     int boardSize,
                     boolean blockTwoEnds,
                     boolean timed,
                     int timeLimitSeconds) {
        this.roomId = roomId;
        this.opponent = opponent;
        this.yourMark = yourMark;
        this.yourTurn = yourTurn;
        this.boardSize = boardSize;
        this.blockTwoEnds = blockTwoEnds;
        this.timed = timed;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public String getRoomId() { return roomId; }
    public String getOpponent() { return opponent; }
    public Mark getYourMark() { return yourMark; }
    public boolean isYourTurn() { return yourTurn; }

    public int getBoardSize() { return boardSize; }
    public boolean isBlockTwoEnds() { return blockTwoEnds; }

    public boolean isTimed() { return timed; }
    public int getTimeLimitSeconds() { return timeLimitSeconds; }
}

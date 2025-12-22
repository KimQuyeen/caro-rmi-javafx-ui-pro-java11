package vn.edu.demo.caro.common.model;

import java.io.Serializable;
import static vn.edu.demo.caro.common.model.Enums.GameEndReason;

public class GameEnd implements Serializable {
    private final String roomId;
    private final String winner; // null for draw
    private final GameEndReason reason;

    public GameEnd(String roomId, String winner, GameEndReason reason) {
        this.roomId = roomId;
        this.winner = winner;
        this.reason = reason;
    }

    public String getRoomId() { return roomId; }
    public String getWinner() { return winner; }
    public GameEndReason getReason() { return reason; }
}

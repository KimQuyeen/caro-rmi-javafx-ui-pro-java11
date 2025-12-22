package vn.edu.demo.caro.common.model;

import java.io.Serializable;
import static vn.edu.demo.caro.common.model.Enums.Mark;

public class GameUpdate implements Serializable {
    private final String roomId;
    private final Move move;
    private final Mark mark;
    private final String nextTurnUser;

    public GameUpdate(String roomId, Move move, Mark mark, String nextTurnUser) {
        this.roomId = roomId;
        this.move = move;
        this.mark = mark;
        this.nextTurnUser = nextTurnUser;
    }

    public String getRoomId() { return roomId; }
    public Move getMove() { return move; }
    public Mark getMark() { return mark; }
    public String getNextTurnUser() { return nextTurnUser; }
}

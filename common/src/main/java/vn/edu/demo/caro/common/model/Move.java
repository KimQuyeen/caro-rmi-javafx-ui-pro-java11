package vn.edu.demo.caro.common.model;

import java.io.Serializable;

public class Move implements Serializable {
    private final int row;
    private final int col;
    private final int moveNo;
    private final String by;

    public Move(int row, int col, int moveNo, String by) {
        this.row = row;
        this.col = col;
        this.moveNo = moveNo;
        this.by = by;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public int getMoveNo() { return moveNo; }
    public String getBy() { return by; }
}

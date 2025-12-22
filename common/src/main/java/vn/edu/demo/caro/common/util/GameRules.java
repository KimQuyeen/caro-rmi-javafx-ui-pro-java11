package vn.edu.demo.caro.common.util;

import vn.edu.demo.caro.common.model.Enums.Mark;

public final class GameRules {
    private GameRules() {}

    public static final int BOARD = 15;
    public static final int WIN = 5;

    
    public static boolean inBounds(int r, int c) {
        return r >= 0 && r < BOARD && c >= 0 && c < BOARD;
    }

    public static boolean isWin(Mark[][] b, int r, int c, Mark m) {
        if (m == Mark.EMPTY) return false;
        return count(b, r, c, 0, 1, m) + count(b, r, c, 0, -1, m) - 1 >= WIN ||
               count(b, r, c, 1, 0, m) + count(b, r, c, -1, 0, m) - 1 >= WIN ||
               count(b, r, c, 1, 1, m) + count(b, r, c, -1, -1, m) - 1 >= WIN ||
               count(b, r, c, 1, -1, m) + count(b, r, c, -1, 1, m) - 1 >= WIN;
    }

    private static int count(Mark[][] b, int r, int c, int dr, int dc, Mark m) {
        int cnt = 0;
        int rr = r, cc = c;
        while (inBounds(rr, cc) && b[rr][cc] == m) {
            cnt++;
            rr += dr; cc += dc;
        }
        return cnt;
    }
}

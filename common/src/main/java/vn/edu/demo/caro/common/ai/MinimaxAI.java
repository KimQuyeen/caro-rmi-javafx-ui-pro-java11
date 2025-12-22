package vn.edu.demo.caro.common.ai;

import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.util.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Local AI (client-side) for "Play vs AI" mode.
 * Java 11 compatible (no records).
 */
public class MinimaxAI {

    public static final class AiMove {
        private final int row;
        private final int col;

        public AiMove(int row, int col) {
            this.row = row;
            this.col = col;
        }

        public int getRow() { return row; }
        public int getCol() { return col; }
    }

    private final int maxDepth;

    public MinimaxAI(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public AiMove bestMove(Mark[][] board, Mark aiMark) {
        Mark human = (aiMark == Mark.X) ? Mark.O : Mark.X;
        List<int[]> candidates = candidateMoves(board);

        int bestScore = Integer.MIN_VALUE;
        int bestR = -1, bestC = -1;

        for (int[] mv : candidates) {
            int r = mv[0], c = mv[1];
            board[r][c] = aiMark;
            int score = minimax(board, maxDepth - 1, false, aiMark, human,
                    Integer.MIN_VALUE, Integer.MAX_VALUE, r, c);
            board[r][c] = Mark.EMPTY;

            if (score > bestScore) {
                bestScore = score;
                bestR = r; bestC = c;
            }
        }

        if (bestR == -1) {
            int mid = GameRules.BOARD / 2;
            return new AiMove(mid, mid);
        }
        return new AiMove(bestR, bestC);
    }

    private int minimax(Mark[][] b, int depth, boolean maximizing,
                        Mark ai, Mark human, int alpha, int beta, int lastR, int lastC) {

        Mark lastMark = b[lastR][lastC];
        if (GameRules.isWin(b, lastR, lastC, lastMark)) {
            return (lastMark == ai) ? 1000000 + depth : -1000000 - depth;
        }
        if (isFull(b) || depth == 0) {
            return evaluate(b, ai, human);
        }

        List<int[]> candidates = candidateMoves(b);

        if (maximizing) {
            int best = Integer.MIN_VALUE;
            for (int[] mv : candidates) {
                int r = mv[0], c = mv[1];
                b[r][c] = ai;
                int score = minimax(b, depth - 1, false, ai, human, alpha, beta, r, c);
                b[r][c] = Mark.EMPTY;
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int[] mv : candidates) {
                int r = mv[0], c = mv[1];
                b[r][c] = human;
                int score = minimax(b, depth - 1, true, ai, human, alpha, beta, r, c);
                b[r][c] = Mark.EMPTY;
                best = Math.min(best, score);
                beta = Math.min(beta, best);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    private boolean isFull(Mark[][] b) {
        for (int r = 0; r < GameRules.BOARD; r++)
            for (int c = 0; c < GameRules.BOARD; c++)
                if (b[r][c] == Mark.EMPTY) return false;
        return true;
    }

    /**
     * Candidate move pruning:
     * Only consider empty cells within distance 2 of existing stones.
     */
    private List<int[]> candidateMoves(Mark[][] b) {
        boolean any = false;
        boolean[][] cand = new boolean[GameRules.BOARD][GameRules.BOARD];
        for (int r = 0; r < GameRules.BOARD; r++) {
            for (int c = 0; c < GameRules.BOARD; c++) {
                if (b[r][c] != Mark.EMPTY) {
                    any = true;
                    for (int dr = -2; dr <= 2; dr++) {
                        for (int dc = -2; dc <= 2; dc++) {
                            int rr = r + dr, cc = c + dc;
                            if (GameRules.inBounds(rr, cc) && b[rr][cc] == Mark.EMPTY) {
                                cand[rr][cc] = true;
                            }
                        }
                    }
                }
            }
        }
        List<int[]> out = new ArrayList<>();
        if (!any) {
            int mid = GameRules.BOARD / 2;
            out.add(new int[]{mid, mid});
            return out;
        }
        for (int r = 0; r < GameRules.BOARD; r++)
            for (int c = 0; c < GameRules.BOARD; c++)
                if (cand[r][c]) out.add(new int[]{r, c});
        return out;
    }

    private int evaluate(Mark[][] b, Mark ai, Mark human) {
        return scoreFor(b, ai) - scoreFor(b, human);
    }

    private int scoreFor(Mark[][] b, Mark m) {
        int score = 0;
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int r=0;r<GameRules.BOARD;r++) {
            for (int c=0;c<GameRules.BOARD;c++) {
                for (int[] d: dirs) {
                    int dr=d[0], dc=d[1];
                    int pr=r-dr, pc=c-dc;
                    if (GameRules.inBounds(pr,pc)) continue;

                    int rr=r, cc=c, run=0;
                    while (GameRules.inBounds(rr,cc)) {
                        Mark cur = b[rr][cc];
                        if (cur==m) run++;
                        else {
                            if (run>0) {
                                boolean leftOpen = isEmpty(b, rr-run*dr, cc-run*dc);
                                boolean rightOpen = (cur==Mark.EMPTY);
                                score += segmentScore(run, leftOpen, rightOpen);
                                run=0;
                            }
                        }
                        rr+=dr; cc+=dc;
                    }
                    if (run>0) {
                        int endR = rr-dr, endC = cc-dc;
                        boolean leftOpen = isEmpty(b, endR-run*dr, endC-run*dc);
                        score += segmentScore(run, leftOpen, false);
                    }
                }
            }
        }
        return score;
    }

    private boolean isEmpty(Mark[][] b, int r, int c) {
        if (!GameRules.inBounds(r,c)) return false;
        return b[r][c]==Mark.EMPTY;
    }

    private int segmentScore(int run, boolean leftOpen, boolean rightOpen) {
        int open = (leftOpen?1:0)+(rightOpen?1:0);
        if (run >= 5) return 200000;
        if (run == 4 && open == 2) return 40000;
        if (run == 4 && open == 1) return 12000;
        if (run == 3 && open == 2) return 5000;
        if (run == 3 && open == 1) return 1200;
        if (run == 2 && open == 2) return 500;
        if (run == 2 && open == 1) return 120;
        if (run == 1 && open == 2) return 30;
        return 0;
    }
}

package vn.edu.demo.caro.common.ai;

import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.util.GameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Optimized Minimax AI
 * - Giới hạn số lượng nhánh (Branching Factor)
 * - Sắp xếp nước đi (Move Ordering)
 * - Đánh nhanh ở độ sâu cao
 */
public class MinimaxAI {

    public static final class AiMove {
        public final int row;
        public final int col;
        public AiMove(int row, int col) { this.row = row; this.col = col; }
    }

    private final int maxDepth;
    // Giới hạn chỉ xem xét 12 nước đi tốt nhất tại mỗi bước để giảm thời gian tính toán
    // Nếu muốn khó hơn nữa (nhưng chậm hơn), tăng số này lên 15-20
    private static final int MAX_BRANCHES = 12; 

    public MinimaxAI(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public AiMove bestMove(Mark[][] board, Mark aiMark) {
        Mark human = (aiMark == Mark.X) ? Mark.O : Mark.X;
        
        // Lấy danh sách các nước đi tiềm năng và SẮP XẾP chúng
        List<int[]> candidates = getSortedCandidates(board, aiMark, human);

        // Nếu bàn cờ trống, đánh giữa
        if (candidates.isEmpty()) {
            return new AiMove(GameRules.BOARD / 2, GameRules.BOARD / 2);
        }

        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = candidates.get(0);
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        // Chỉ duyệt qua Top MAX_BRANCHES nước đi tốt nhất
        int limit = Math.min(candidates.size(), MAX_BRANCHES);
        
        for (int i = 0; i < limit; i++) {
            int[] mv = candidates.get(i);
            int r = mv[0], c = mv[1];

            board[r][c] = aiMark;
            // Gọi đệ quy
            int score = minimax(board, maxDepth - 1, false, aiMark, human, alpha, beta);
            board[r][c] = Mark.EMPTY;

            if (score > bestScore) {
                bestScore = score;
                bestMove = mv;
            }
            alpha = Math.max(alpha, bestScore);
        }

        return new AiMove(bestMove[0], bestMove[1]);
    }

    private int minimax(Mark[][] b, int depth, boolean maximizing,
                        Mark ai, Mark human, int alpha, int beta) {
        
        // 1. Nếu hết độ sâu, tính điểm bàn cờ
        if (depth == 0) {
            return evaluate(b, ai, human);
        }

        // 2. Lấy các nước đi lân cận
        List<int[]> candidates = getCandidatesRaw(b);
        if (candidates.isEmpty()) return evaluate(b, ai, human);

        // Giới hạn nhánh tìm kiếm ở các tầng sâu để chạy nhanh
        int currentLimit = (depth >= 3) ? 8 : MAX_BRANCHES; 
        int count = 0;

        if (maximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] mv : candidates) {
                if (count++ >= currentLimit) break; // Cắt tỉa nhánh thừa
                
                int r = mv[0], c = mv[1];
                
                // Check thắng ngay lập tức để tiết kiệm thời gian
                if (checkWinFast(b, r, c, ai)) return 100000 + depth;

                b[r][c] = ai;
                int eval = minimax(b, depth - 1, false, ai, human, alpha, beta);
                b[r][c] = Mark.EMPTY;

                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] mv : candidates) {
                if (count++ >= currentLimit) break;

                int r = mv[0], c = mv[1];

                // Check thua ngay lập tức
                if (checkWinFast(b, r, c, human)) return -100000 - depth;

                b[r][c] = human;
                int eval = minimax(b, depth - 1, true, ai, human, alpha, beta);
                b[r][c] = Mark.EMPTY;

                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    // --- Helpers ---

    // Lấy các nước đi tiềm năng và chấm điểm sơ bộ để sắp xếp
    private List<int[]> getSortedCandidates(Mark[][] b, Mark ai, Mark human) {
        List<int[]> candidates = getCandidatesRaw(b);
        // Sắp xếp: Ưu tiên ô nào tạo ra điểm cao cục bộ (gần nhiều quân mình hoặc quân địch)
        candidates.sort((m1, m2) -> {
            int s1 = quickScore(b, m1[0], m1[1], ai, human);
            int s2 = quickScore(b, m2[0], m2[1], ai, human);
            return Integer.compare(s2, s1); // Giảm dần
        });
        return candidates;
    }

    // Lấy các ô trống có quân xung quanh (phạm vi 2 ô)
    private List<int[]> getCandidatesRaw(Mark[][] b) {
        List<int[]> list = new ArrayList<>();
        int n = GameRules.BOARD;
        boolean[][] visited = new boolean[n][n];

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (b[r][c] != Mark.EMPTY) {
                    for (int dr = -2; dr <= 2; dr++) {
                        for (int dc = -2; dc <= 2; dc++) {
                            if (dr == 0 && dc == 0) continue;
                            int nr = r + dr;
                            int nc = c + dc;
                            if (nr >= 0 && nr < n && nc >= 0 && nc < n && 
                                b[nr][nc] == Mark.EMPTY && !visited[nr][nc]) {
                                list.add(new int[]{nr, nc});
                                visited[nr][nc] = true;
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    // Chấm điểm nhanh 1 ô (Heuristic đơn giản để sắp xếp)
    private int quickScore(Mark[][] b, int r, int c, Mark ai, Mark human) {
        int score = 0;
        // Ưu tiên trung tâm
        if (r > 4 && r < 10 && c > 4 && c < 10) score += 5;
        
        // Kiểm tra sơ bộ 4 hướng xem có tạo thành hàng không
        score += countConsecutive(b, r, c, ai) * 2;   // Tấn công
        score += countConsecutive(b, r, c, human) * 3; // Phòng thủ (Ưu tiên chặn)
        return score;
    }

    private int countConsecutive(Mark[][] b, int r, int c, Mark m) {
        int max = 0;
        int[][] dirs = {{1,0}, {0,1}, {1,1}, {1,-1}};
        for (int[] d : dirs) {
            int cnt = 0;
            for (int k = 1; k <= 4; k++) { // Check 4 ô liên tiếp
                int nr = r + d[0]*k, nc = c + d[1]*k;
                if (nr>=0 && nr<15 && nc>=0 && nc<15 && b[nr][nc] == m) cnt++;
                else break;
            }
            if (cnt > max) max = cnt;
        }
        return max;
    }

    private boolean checkWinFast(Mark[][] b, int r, int c, Mark m) {
        // Logic check win nhanh (bạn có thể dùng GameRules.isWin nhưng viết lại ở đây cho nhanh nếu muốn)
        return GameRules.isWin(b, r, c, m);
    }

    // Hàm lượng giá toàn bàn cờ
    private int evaluate(Mark[][] b, Mark ai, Mark human) {
        // Tính tổng điểm tấn công - tổng điểm bị đe dọa
        return calculateBoardScore(b, ai) - calculateBoardScore(b, human); 
    }

    private int calculateBoardScore(Mark[][] b, Mark m) {
        int score = 0;
        // Duyệt các chuỗi quân cờ để cộng điểm
        // (Đây là bản giản lược để chạy nhanh, logic đầy đủ sẽ rất dài)
        // Ưu tiên:
        // 5 ô: Thắng tuyệt đối (đã check ở trên)
        // 4 ô thoáng 2 đầu: Rất cao
        // 4 ô thoáng 1 đầu: Cao
        // 3 ô thoáng 2 đầu: Nguy hiểm
        
        // Để code gọn và nhanh, ta dùng thuật toán quét đơn giản:
        int[] dr = {1, 0, 1, 1};
        int[] dc = {0, 1, 1, -1};
        
        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                if (b[r][c] != m) continue;
                
                for (int i = 0; i < 4; i++) {
                    int count = 1; 
                    int openEnds = 0;
                    
                    // Check lùi
                    int pr = r - dr[i], pc = c - dc[i];
                    if (pr >= 0 && pr < 15 && pc >= 0 && pc < 15 && b[pr][pc] == Mark.EMPTY) openEnds++;
                    
                    // Check tới
                    int k = 1;
                    while (true) {
                        int nr = r + dr[i]*k, nc = c + dc[i]*k;
                        if (nr >= 0 && nr < 15 && nc >= 0 && nc < 15 && b[nr][nc] == m) {
                            count++;
                            k++;
                        } else {
                            if (nr >= 0 && nr < 15 && nc >= 0 && nc < 15 && b[nr][nc] == Mark.EMPTY) openEnds++;
                            break;
                        }
                    }
                    
                    // Chỉ tính điểm cho quân đầu tiên của chuỗi để tránh trùng lặp
                    if (count >= 5) score += 100000;
                    else if (count == 4) {
                        if (openEnds == 2) score += 10000;
                        else if (openEnds == 1) score += 1000;
                    } else if (count == 3) {
                        if (openEnds == 2) score += 1000;
                        else if (openEnds == 1) score += 100;
                    } else if (count == 2) {
                        if (openEnds == 2) score += 100;
                    }
                }
            }
        }
        return score;
    }
}
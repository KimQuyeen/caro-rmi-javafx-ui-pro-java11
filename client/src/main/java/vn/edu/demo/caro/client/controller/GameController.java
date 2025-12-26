package vn.edu.demo.caro.client.controller;

import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.edu.demo.caro.client.core.AppContext;
import vn.edu.demo.caro.client.core.ClientCallbackImpl;
import vn.edu.demo.caro.client.core.SoundManager;
import vn.edu.demo.caro.client.core.WithContext;
import vn.edu.demo.caro.common.model.Enums.GameEndReason;
import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.model.UserPublicProfile.FriendStatus;
import vn.edu.demo.caro.common.model.*;
import vn.edu.demo.caro.common.ai.MinimaxAI;
import java.util.concurrent.Executors;
import java.time.Instant;

public class GameController implements WithContext {

    private AppContext ctx;
    private ClientCallbackImpl callback;

    @FXML private Label lbMe;
    @FXML private Label lbOpponent;
@FXML private javafx.scene.layout.StackPane rootPane;
    @FXML private Label lbTitle;
    @FXML private Label lbSub;
    @FXML private GridPane gridBoard;
    @FXML private ScrollPane spBoard;

    @FXML private ListView<ChatMessage> lvChat;
    @FXML private TextField tfChat;

    @FXML private Button btnUndo;
    @FXML private Button btnRedo;

    @FXML private Label lbTimer;
@FXML private Button btnSound;
    
    private MinimaxAI aiEngine;
    private Mark aiMark; 
// Dùng thread riêng cho AI nghĩ để không đơ màn hình
    private final java.util.concurrent.ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private javafx.animation.Timeline timerTimeline;
    private boolean timed;
    private long turnDeadlineMillis;

    private final ObservableList<ChatMessage> chatItems = FXCollections.observableArrayList();

    private int boardSize = 15;

    private Button[][] cellButtons;
    private Label[][] cellMarks;
    private Mark[][] board;

    private Mark myMark = Mark.EMPTY;
    private boolean myTurn = false;
    private String opponent = "?";
    private boolean finished = false;

    private boolean aiEnabled = false;

    // ===== Post-game / Rematch =====
    private volatile boolean opponentRequestedRematch = false;
    private volatile boolean waitingRematchDecision = false;

    private Alert endGameAlert;
    private Stage rematchStage;
    private volatile boolean suppressPostGameChoice = false;

    // ---------------- FX helper ----------------
    private void fx(Runnable r) {
        if (r == null) return;
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    // ---------------- RMI helper ----------------
    @FunctionalInterface
    private interface RemoteJob { void run() throws Exception; }

    private void runRemote(String action, RemoteJob job) {
        if (ctx == null || ctx.io() == null) return;
        ctx.io().execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                fx(() -> showInfo("Lỗi", action + ": " + e.getMessage()));
            }
        });
    }

   @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;
updateSoundButton();
        // Lấy cấu hình AI từ AiViewController
        aiEnabled = (boolean) ctx.stage.getProperties().getOrDefault("ai.enabled", false);

        if (aiEnabled) {
            // --- SETUP CHẾ ĐỘ AI ---
            // Lấy độ khó và ai đi trước (key phải khớp với AiViewController: "ai.depth", "ai.first")
            int depth = (int) ctx.stage.getProperties().getOrDefault("ai.depth", 3);
            boolean aiFirst = (boolean) ctx.stage.getProperties().getOrDefault("ai.first", false);
            
            aiEngine = new MinimaxAI(depth);
            boardSize = 15;
            setupBoard(boardSize);
            finished = false;
            
            // Thiết lập quân cờ
            if (aiFirst) {
                // Máy đi trước => Máy X, Bạn O
                myMark = Mark.O; 
                aiMark = Mark.X; 
                myTurn = false;
                requestAiMove(); // Gọi máy đánh ngay nước đầu
            } else {
                // Bạn đi trước => Bạn X, Máy O
                myMark = Mark.X; 
                aiMark = Mark.O; 
                myTurn = true;
            }
            
            opponent = "AI (Level " + depth + ")";
            if (lbMe != null) lbMe.setText("Bạn (" + myMark + ")");
            if (lbOpponent != null) lbOpponent.setText(opponent);
            
            appendChat(new ChatMessage("SYSTEM", "AI", "Bắt đầu chơi với máy! Level " + depth, Instant.now()));
            setBoardEnabled(myTurn);
            refreshHeader();
            return; // QUAN TRỌNG: Return luôn để không chạy logic Online bên dưới
        }

        // --- SETUP CHẾ ĐỘ ONLINE (Chỉ chạy khi aiEnabled = false) ---
        Object cbObj = ctx.stage.getProperties().get("callback");
        if (cbObj instanceof ClientCallbackImpl) {
            this.callback = (ClientCallbackImpl) cbObj;
            this.callback.bindGame(this);
        }

        initChatUI();

        if (btnUndo != null) btnUndo.setDisable(true);
        if (btnRedo != null) btnRedo.setDisable(true);

        Object pending = ctx.stage.getProperties().remove("pending.gameStart");
        if (pending instanceof GameStart) {
            onGameStart((GameStart) pending);
        } else {
            setupBoard(15);
            appendChat(new ChatMessage("SYSTEM", "ROOM", "Đang chờ đủ 2 người để bắt đầu...", Instant.now()));
            refreshHeader();
        }
    }
    @FXML
    private void onToggleSound() {
        SoundManager.getInstance().toggleMute();
        updateSoundButton();
    }

    // [THÊM] Hàm cập nhật giao diện nút âm thanh
    private void updateSoundButton() {
        if (btnSound == null) return;
        boolean isMuted = SoundManager.getInstance().isMuted();
        if (isMuted) {
            btnSound.setText("🔇 Tắt");
            btnSound.setStyle("-fx-opacity: 0.7;");
        } else {
            btnSound.setText("🔊 Bật");
            btnSound.setStyle("-fx-opacity: 1.0;");
        }
    }

    private boolean isBoardFull() {
        if (board == null) return true;
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (board[r][c] == Mark.EMPTY) return false;
            }
        }
        return true;
    }
    // ============================================================
    // Click opponent (optional)
    // ============================================================
    @FXML
    private void onOpponentClicked() {
        if (aiEnabled) return;
        // Lấy tên đối thủ hiện tại
        String target = (opponent == null) ? "" : opponent.trim();
        
        // Kiểm tra hợp lệ
        if (target.isBlank() || "?".equals(target)) return;
        if (ctx.username != null && ctx.username.equalsIgnoreCase(target)) return; // Không xem chính mình

        // Gọi Server lấy thông tin (Chạy bất đồng bộ để không treo UI)
        runRemote("Lấy thông tin đối thủ", () -> {
            // Gọi hàm get profile bên server
            UserPublicProfile profile = ctx.lobby.getUserPublicProfile(ctx.username, target);
            
            // Có dữ liệu thì vẽ lên UI
            fx(() -> showUserProfileDialog(profile));
        });
    }

    private void showUserProfileDialog(UserPublicProfile p) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông tin người chơi");
        dialog.setHeaderText("Hồ sơ: " + p.getUsername());

        // Tạo nút Đóng
        ButtonType closeBtn = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeBtn);

        // Layout dạng lưới để hiện thông số
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 20; -fx-font-size: 14px;");

        // Hàng 1: Rank & Elo
        grid.add(new Label("Xếp hạng:"), 0, 0);
        Label lbRank = new Label("#" + p.getRank() + " (Elo: " + p.getElo() + ")");
        lbRank.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
        grid.add(lbRank, 1, 0);

        // Hàng 2: Số trận
        grid.add(new Label("Tổng số trận:"), 0, 1);
        grid.add(new Label(String.valueOf(p.getGamesPlayed())), 1, 1);

        // Hàng 3: Thắng / Thua / Hòa
        grid.add(new Label("Thắng/Thua/Hòa:"), 0, 2);
        String stats = String.format("%d / %d / %d", p.getWins(), p.getLosses(), p.getDraws());
        grid.add(new Label(stats), 1, 2);

        // Hàng 4: Tỉ lệ thắng
        grid.add(new Label("Tỉ lệ thắng:"), 0, 3);
        Label lbRate = new Label(String.format("%.1f%%", p.getWinRate()));
        if (p.getWinRate() >= 50) lbRate.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        grid.add(lbRate, 1, 3);

        // Hàng 5: Nút kết bạn
        Button btnAddFriend = new Button();
        btnAddFriend.setMaxWidth(Double.MAX_VALUE);
        GridPane.setColumnSpan(btnAddFriend, 2); // Nút dài ra 2 cột

        // Kiểm tra trạng thái bạn bè để hiển thị nút
        updateFriendButtonState(btnAddFriend, p.getFriendStatus(), p.getUsername());

        grid.add(btnAddFriend, 0, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.show();
    }

    // Hàm xử lý nút kết bạn
    private void updateFriendButtonState(Button btn, FriendStatus status, String targetUser) {
        switch (status) {
            case FRIEND:
                btn.setText("Đã là bạn bè");
                btn.setDisable(true);
                btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                break;
            case OUTGOING_PENDING:
                btn.setText("Đã gửi lời mời (Chờ đồng ý)");
                btn.setDisable(true);
                break;
            case INCOMING_PENDING:
                btn.setText("Người này đã gửi lời mời cho bạn!");
                btn.setDisable(true); // Hoặc bạn có thể code thêm nút chấp nhận tại đây
                break;
            case NOT_FRIEND:
            default:
                btn.setText("Gửi lời mời kết bạn");
                btn.setDisable(false);
                btn.setStyle(""); // Default style
                
                // Sự kiện khi bấm nút
                btn.setOnAction(e -> {
                    btn.setDisable(true);
                    btn.setText("Đang gửi...");
                    
                    // Gọi Server gửi lời mời
                    runRemote("Gửi kết bạn", () -> {
                        boolean sent = ctx.lobby.sendFriendRequestByName(ctx.username, targetUser);
                        fx(() -> {
                            if (sent) {
                                btn.setText("Đã gửi lời mời");
                                showInfo("Thành công", "Đã gửi lời mời kết bạn tới " + targetUser);
                            } else {
                                btn.setText("Gửi lời mời kết bạn"); // Reset nếu lỗi
                                btn.setDisable(false);
                            }
                        });
                    });
                });
                break;
        }
    }

    // ============================================================
    // Server callback handlers (ClientCallbackImpl gọi vào đây)
    // ============================================================

    public void onUndoRequested(String roomId, String from) {
        // FIX: không dùng showAndWait (tránh “đứng” vì block FX)
        fx(() -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Undo request");
            a.setHeaderText(from + " yêu cầu Undo");
            a.setContentText("Bạn có đồng ý không?");

            ButtonType accept = new ButtonType("Accept", ButtonBar.ButtonData.YES);
            ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(accept, reject);

            a.setOnHidden(ev -> {
                ButtonType chosen = a.getResult();
                boolean ok = (chosen == accept);
                runRemote("respondUndo", () -> ctx.lobby.respondUndo(roomId, ctx.username, ok));
            });

            a.show();
        });
    }

    public void onRedoRequested(String roomId, String from) {
        // FIX: không dùng showAndWait
        fx(() -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Đề nghị hòa");
            a.setHeaderText(from + " đề nghị hòa");
            a.setContentText("Bạn có đồng ý không?");

            ButtonType accept = new ButtonType("Accept", ButtonBar.ButtonData.YES);
            ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(accept, reject);

            a.setOnHidden(ev -> {
                ButtonType chosen = a.getResult();
                boolean ok = (chosen == accept);
                runRemote("respondRedo", () -> ctx.lobby.respondRedo(roomId, ctx.username, ok));
            });

            a.show();
        });
    }

    public void onAnnouncement(String msg) {
        appendChat(new ChatMessage("SYSTEM", "ANNOUNCE", msg, Instant.now()));
    }

    public void onRematchRequested(String roomId, String from) {
        fx(() -> {
            opponentRequestedRematch = true;
            suppressPostGameChoice = true;
            closeEndGameAlertIfAny();

            if (waitingRematchDecision) {
                appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId,
                        from + " cũng muốn Rematch. Đang chờ server bắt đầu ván mới...", Instant.now()));
                return;
            }

            showRematchPromptStage(roomId, from);
        });
    }

    public void onGameStart(GameStart start) {
        fx(() -> {
            aiEnabled = false;

            opponentRequestedRematch = false;
            waitingRematchDecision = false;
            suppressPostGameChoice = false;

            closeEndGameAlertIfAny();
            closeRematchStageIfAny();

            ctx.currentRoomId = start.getRoomId();
            opponent = start.getOpponent();
            myMark = start.getYourMark();
            myTurn = start.isYourTurn();
            finished = false;

            boardSize = start.getBoardSize();
            setupBoard(boardSize);

            if (lbMe != null) lbMe.setText("Bạn: " + ctx.username);
            if (lbOpponent != null) lbOpponent.setText("Đối thủ: " + opponent);

            appendChat(new ChatMessage(
                    "SYSTEM",
                    "ROOM:" + start.getRoomId(),
                    "Bắt đầu trận. Bạn là " + myMark +
                            " | Bàn: " + start.getBoardSize() +
                            " | " + (start.isBlockTwoEnds() ? "Chặn 2 đầu" : "Không chặn") +
                            " | " + (start.isTimed() ? (start.getTimeLimitSeconds() + "s/lượt") : "Không giới hạn thời gian"),
                    Instant.now()
            ));

            if (btnUndo != null) btnUndo.setDisable(false);
            if (btnRedo != null) btnRedo.setDisable(false);

            setBoardEnabled(myTurn);
            refreshHeader();
        });
    }

    public void onGameUpdate(GameUpdate update) {
        if (update == null || finished) return;

        fx(() -> {
            applyMoveIfPossible(update.getMove(), update.getMark());
            myTurn = ctx.username != null && ctx.username.equals(update.getNextTurnUser());
            setBoardEnabled(myTurn);
            refreshHeader();
        });
    }

    public void onGameEnd(GameEnd end) {
        if (end == null) return;
        if (finished) return;
        finished = true;

        fx(() -> {
            stopCountdown();
            setBoardEnabled(false);
            suppressPostGameChoice = false;

            if (end.getReason() == GameEndReason.ABORT) {
                appendChat(new ChatMessage("SYSTEM", "ROOM:" + end.getRoomId(),
                        "Đối thủ đã rời phòng. Phòng đang chờ người chơi khác vào...", Instant.now()));
                refreshHeader();
                return;
            }

            String msg = buildEndMessage(end);
            appendChat(new ChatMessage("SYSTEM", "ROOM:" + end.getRoomId(), msg, Instant.now()));
            refreshHeader();

            PauseTransition pt = new PauseTransition(Duration.millis(150));
            pt.setOnFinished(ev -> {
                if (waitingRematchDecision || opponentRequestedRematch) return;
                showPostGameChoicePopup(msg);
            });
            pt.play();
        });

        if (end.getWinner() == null) {
            // Hòa
            SoundManager.getInstance().playNotify();
        } else if (end.getWinner().equals(ctx.username)) {
            // Mình thắng -> Nhạc thắng + Hiệu ứng
            SoundManager.getInstance().playWin();
            showVictoryEffect(); // Gọi hàm hiệu ứng (viết ở dưới)
        } else {
            // Mình thua
            SoundManager.getInstance().playLose();
        }
    }

    public void onRoomChat(ChatMessage msg) {
        appendChat(msg);
    }

    public void onUndoResult(String roomId, boolean accepted, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
    }

    public void onRedoResult(String roomId, boolean accepted, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
    }

    public void onReturnToLobby(String roomId, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
        fx(this::onBackToMain);
    }

    // ============================================================
    // Snapshot (authoritative)
    // ============================================================
    public void applySnapshot(GameSnapshot snap) {
        if (snap == null) return;

        fx(() -> {
            int snapSize = snap.getBoardSize();
            if (snapSize <= 0) snapSize = 15;

            boolean needRebuild = (board == null || cellButtons == null || cellMarks == null || snapSize != boardSize);
            if (needRebuild) {
                boardSize = snapSize;
                setupBoard(boardSize);
            }

            Mark[][] b = snap.getBoard();
            if (b != null) {
                int size = Math.min(boardSize, b.length);
                for (int r = 0; r < boardSize; r++) {
                    for (int c = 0; c < boardSize; c++) {
                        Mark m = Mark.EMPTY;
                        if (r < size && b[r] != null && c < b[r].length && b[r][c] != null) {
                            m = b[r][c];
                        }
                        board[r][c] = m;

                        Label lb = cellMarks[r][c];
                        Button btn = cellButtons[r][c];

                        if (lb != null) {
                            lb.setText(m == Mark.EMPTY ? "" : (m == Mark.X ? "X" : "O"));
                            lb.getStyleClass().removeAll("mark-x", "mark-o");
                            if (m == Mark.X) lb.getStyleClass().add("mark-x");
                            if (m == Mark.O) lb.getStyleClass().add("mark-o");
                        }
                        if (btn != null) {
                            boolean empty = (m == Mark.EMPTY);
                            btn.setMouseTransparent(!empty);
                            if (empty) {
                                final int rr = r, cc = c;
                                btn.setOnAction(e -> onCellClick(rr, cc));
                            } else {
                                btn.setOnAction(null);
                            }
                        }
                    }
                }
            }

            updateStateFromSnapshotOnly(snap);
            refreshHeader();
        });
    }

    private void updateStateFromSnapshotOnly(GameSnapshot snap) {
        String t = snap.getTurn();
        if (t == null || t.isBlank()) {
            myTurn = false;
            finished = false;
            setBoardEnabled(false);
            if (lbTimer != null) lbTimer.setText("Waiting for opponent...");
        } else {
            myTurn = (ctx.username != null && ctx.username.equals(t));
            setBoardEnabled(!finished && myTurn);
        }

        timed = snap.isTimed();
        turnDeadlineMillis = snap.getTurnDeadlineMillis();
        startOrStopCountdown();
    }

    // ============================================================
    // UI actions
    // ============================================================

    @FXML
    private void onSendChat() {
        String text = (tfChat != null && tfChat.getText() != null) ? tfChat.getText().trim() : "";
        if (text.isBlank()) return;
        if (ctx.currentRoomId == null) return;

        final String roomId = ctx.currentRoomId;
        final ChatMessage msg = new ChatMessage(ctx.username, "ROOM:" + roomId, text, Instant.now());

        tfChat.clear();

        runRemote("sendRoomChat", () -> ctx.lobby.sendRoomChat(roomId, msg));

        fx(() -> { if (gridBoard != null) gridBoard.requestFocus(); });
    }

    @FXML
    private void onUndo() {
        if (aiEnabled || finished) return;
        final String roomId = ctx.currentRoomId;
        final String user = ctx.username;
        runRemote("requestUndo", () -> ctx.lobby.requestUndo(roomId, user));
    }

    @FXML
    private void onRedo() {
        if (aiEnabled || finished) return;
        final String roomId = ctx.currentRoomId;
        final String user = ctx.username;
        runRemote("requestRedo", () -> ctx.lobby.requestRedo(roomId, user));
    }

    @FXML
    private void onResign() {
        if (finished || aiEnabled) return;
        final String roomId = ctx.currentRoomId;
        final String user = ctx.username;
        runRemote("resign", () -> ctx.lobby.resign(roomId, user));
    }

    @FXML
    private void onBackToMain() {
        stopCountdown();

        final String roomId = ctx.currentRoomId;
        final String user = ctx.username;

        ctx.currentRoomId = null;
        ctx.sceneManager.showMain();

        if (!aiEnabled && roomId != null) {
            runRemote("leaveRoom", () -> ctx.lobby.leaveRoom(user, roomId));
        }
    }

    // ============================================================
    // Board
    // ============================================================

    private void setupBoard(int size) {
        if (gridBoard == null) return;

        boardSize = size;
        board = new Mark[size][size];
        cellButtons = new Button[size][size];
        cellMarks = new Label[size][size];

        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) board[r][c] = Mark.EMPTY;

        gridBoard.getChildren().clear();
        gridBoard.getColumnConstraints().clear();
        gridBoard.getRowConstraints().clear();

        if (!gridBoard.getStyleClass().contains("game-grid")) {
            gridBoard.getStyleClass().add("game-grid");
        }

        double cellSize = (size <= 12) ? 44 : (size <= 15 ? 38 : 30);

        for (int c = 0; c < size; c++) gridBoard.getColumnConstraints().add(new ColumnConstraints(cellSize));
        for (int r = 0; r < size; r++) gridBoard.getRowConstraints().add(new RowConstraints(cellSize));

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                Button btn = new Button("");
                btn.setMinSize(cellSize, cellSize);
                btn.setPrefSize(cellSize, cellSize);
                btn.setMaxSize(cellSize, cellSize);
                btn.getStyleClass().add("game-cell-btn");

                final int rr = r, cc = c;
                btn.setOnAction(e -> onCellClick(rr, cc));

                Label markLb = new Label("");
                markLb.setMouseTransparent(true);
                markLb.setMinSize(cellSize, cellSize);
                markLb.setPrefSize(cellSize, cellSize);
                markLb.getStyleClass().add("game-cell-label");

                StackPane cell = new StackPane(btn, markLb);
                cellButtons[r][c] = btn;
                cellMarks[r][c] = markLb;

                gridBoard.add(cell, c, r);
            }
        }
    }

    private void onCellClick(int r, int c) {
        // CHỈNH SỬA: Chỉ return khi finished, KHÔNG chặn aiEnabled
        if (finished) return;

        // --- XỬ LÝ CHẾ ĐỘ AI ---
        if (aiEnabled) {
            // Nếu chưa tới lượt người (đang lượt máy) hoặc ô đã đánh -> Bỏ qua
            if (!myTurn) return; 
            if (board[r][c] != Mark.EMPTY) return;

            // 1. Người đánh
            applyLocalMove(r, c, myMark);

            // 2. Kiểm tra người thắng chưa
            if (vn.edu.demo.caro.common.util.GameRules.isWin(board, r, c, myMark)) {
                endGameLocal("Bạn thắng rồi!");
                return;
            }
            // Kiểm tra hòa
            if (isBoardFull()) {
                endGameLocal("Hòa! Bàn cờ đã đầy.", null);
                return;
            }

            // 3. Chuyển lượt cho máy
            myTurn = false;
            setBoardEnabled(false); // Khóa bàn cờ để người chơi không click lung tung
            requestAiMove();        // Gọi máy tính toán
            return;
        }

        // --- XỬ LÝ CHẾ ĐỘ ONLINE (Giữ nguyên) ---
        if (!myTurn) return;
        if (ctx.currentRoomId == null || ctx.currentRoomId.isBlank()) return;
        if (r < 0 || c < 0 || r >= boardSize || c >= boardSize) return;
        if (board == null || board[r][c] != Mark.EMPTY) return;

        myTurn = false;
        setBoardEnabled(false);
        refreshHeader();

        final String roomId = ctx.currentRoomId;
        final String user = ctx.username;

        runRemote("makeMove", () -> ctx.lobby.makeMove(roomId, user, r, c));
    }
   private void requestAiMove() {
        // Chạy trong thread phụ để UI không bị đứng
        aiExecutor.submit(() -> {
            // --- XÓA HOẶC COMMENT DÒNG NÀY ---
            // try { Thread.sleep(600); } catch (Exception e) {} 
            // ----------------------------------

            // Máy tính toán nước đi tốt nhất
            MinimaxAI.AiMove best = aiEngine.bestMove(board, aiMark);

            // Cập nhật lại UI (Bắt buộc dùng fx())
            fx(() -> {
                if (finished) return;
                
                // Máy đánh
               applyLocalMove(best.row, best.col, aiMark);
if (vn.edu.demo.caro.common.util.GameRules.isWin(board, best.row, best.col, aiMark)) {
                    endGameLocal("Máy thắng! Gà quá ^^", aiMark);
                } else {
                    // Trả lượt cho người
                    myTurn = true;
                    setBoardEnabled(true);
                }
            });
        });
    }

// Sửa lại hàm này để nhận 2 tham số: (String message, Mark winner)
    private void endGameLocal(String message, Mark winner) {
        finished = true;
        setBoardEnabled(false);
        
        // Xác định kết quả để hiện lên Label phụ
        String result;
        if (winner == null) {
            result = "Hòa";
        } else if (winner == myMark) {
            result = "Bạn Thắng";
        } else {
            result = "Máy Thắng";
        }
        
        if (lbSub != null) lbSub.setText(result);
        
        // Thêm vào chat và hiện thông báo
        appendChat(new ChatMessage("SYSTEM", "AI", message, Instant.now()));
        showInfo("Kết thúc", message);
    }

private void applyLocalMove(int r, int c, Mark m) {
    board[r][c] = m;
    Button btn = cellButtons[r][c];
    Label lb = cellMarks[r][c];
    
    // Vẽ giao diện
    lb.setText(m == Mark.X ? "X" : "O");
    lb.getStyleClass().removeAll("mark-x", "mark-o");
    lb.getStyleClass().add(m == Mark.X ? "mark-x" : "mark-o");
    
    btn.setMouseTransparent(true); // Không cho click lại ô này
}

private void endGameLocal(String msg) {
    finished = true;
    showInfo("Kết thúc", msg);
}

    private void setBoardEnabled(boolean enabled) {
        if (gridBoard != null) gridBoard.setDisable(!enabled);
    }

    private void applyMoveIfPossible(Move mv, Mark mark) {
        if (mv == null || mark == null) return;

        int r = mv.getRow();
        int c = mv.getCol();
        if (r < 0 || c < 0 || r >= boardSize || c >= boardSize) return;
        if (board == null) return;
        if (board[r][c] != Mark.EMPTY) return;

        board[r][c] = mark;

        Label markLb = cellMarks[r][c];
        Button btn = cellButtons[r][c];

        if (markLb != null) {
            markLb.setText(mark == Mark.X ? "X" : "O");
            markLb.getStyleClass().removeAll("mark-x", "mark-o");
            if (mark == Mark.X) markLb.getStyleClass().add("mark-x");
            if (mark == Mark.O) markLb.getStyleClass().add("mark-o");
        }
        if (btn != null) {
            btn.setMouseTransparent(true);
            btn.setOnAction(null);
        }
    }

    // ============================================================
    // Post-game popups
    // ============================================================

    private void showPostGameChoicePopup(String headerMsg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        endGameAlert = a;

        a.setTitle("Kết thúc ván");
        a.setHeaderText(headerMsg);
        a.setContentText("Bạn muốn làm gì?");

        ButtonType rematch = new ButtonType("Request rematch", ButtonBar.ButtonData.YES);
        ButtonType lobby   = new ButtonType("Return to lobby", ButtonBar.ButtonData.NO);
        ButtonType close   = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getButtonTypes().setAll(rematch, lobby, close);

        a.setOnHidden(ev -> {
            ButtonType chosen = a.getResult();
            if (endGameAlert == a) endGameAlert = null;

            if (suppressPostGameChoice) return;
            if (chosen == null) return;
            if (chosen.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) return;

            final String roomId = ctx.currentRoomId;
            if (roomId == null) return;

            if (chosen == rematch) {
                waitingRematchDecision = true;
                runRemote("submitPostGameChoice",
                        () -> ctx.lobby.submitPostGameChoice(roomId, ctx.username, Enums.PostGameChoice.REMATCH));
                appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId,
                        "Đã gửi yêu cầu Rematch. Đang chờ đối thủ...", Instant.now()));
            } else if (chosen == lobby) {
                runRemote("submitPostGameChoice",
                        () -> ctx.lobby.submitPostGameChoice(roomId, ctx.username, Enums.PostGameChoice.RETURN));
            }
        });

        a.show();
    }

    private void showRematchPromptStage(String roomId, String from) {
        if (rematchStage != null && rematchStage.isShowing()) return;

        Stage st = new Stage();
        rematchStage = st;

        st.initOwner(ctx.stage);
        st.initModality(Modality.APPLICATION_MODAL);
        st.setTitle("Rematch");

        Label header = new Label(from + " muốn Rematch");
        header.setStyle("-fx-text-fill: black; -fx-font-size: 16px; -fx-font-weight: bold;");
        Label body = new Label("Bạn đồng ý rematch hay về lobby?");
        body.setStyle("-fx-text-fill: black; -fx-font-size: 13px;");

        Button btnYes = new Button("Accept Rematch");
        Button btnNo  = new Button("Return to lobby");

        btnYes.setOnAction(e -> {
            waitingRematchDecision = true;
            runRemote("submitPostGameChoice",
                    () -> ctx.lobby.submitPostGameChoice(roomId, ctx.username, Enums.PostGameChoice.REMATCH));
            appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId,
                    "Bạn đã đồng ý Rematch. Đang chờ server bắt đầu ván mới...", Instant.now()));
            opponentRequestedRematch = false;
            st.close();
        });

        btnNo.setOnAction(e -> {
            runRemote("submitPostGameChoice",
                    () -> ctx.lobby.submitPostGameChoice(roomId, ctx.username, Enums.PostGameChoice.RETURN));
            opponentRequestedRematch = false;
            st.close();
        });

        HBox buttons = new HBox(10, btnYes, btnNo);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, header, body, buttons);
        root.setStyle("-fx-padding: 16; -fx-background-color: white;");

        st.setScene(new Scene(root));
        st.setOnHidden(e -> { if (rematchStage == st) rematchStage = null; });

        st.show(); // non-blocking
    }

    private void closeEndGameAlertIfAny() {
        if (endGameAlert != null) {
            try { endGameAlert.close(); } catch (Exception ignored) {}
            endGameAlert = null;
        }
    }

    private void closeRematchStageIfAny() {
        if (rematchStage != null) {
            try { rematchStage.close(); } catch (Exception ignored) {}
            rematchStage = null;
        }
    }

    // ============================================================
    // Timer
    // ============================================================

    private void startOrStopCountdown() {
        stopCountdown();

        if (!timed || turnDeadlineMillis <= 0) {
            if (lbTimer != null) lbTimer.setText("No timer");
            return;
        }

        timerTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(0), e -> updateTimerLabel()),
                new javafx.animation.KeyFrame(Duration.seconds(1))
        );
        timerTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timerTimeline.play();
    }

    private void stopCountdown() {
        if (timerTimeline != null) {
            timerTimeline.stop();
            timerTimeline = null;
        }
    }

    private void updateTimerLabel() {
        long now = System.currentTimeMillis();
        long remainSec = Math.max(0, (turnDeadlineMillis - now) / 1000);
        if (lbTimer != null) lbTimer.setText((myTurn ? "Your turn" : "Opponent") + " • " + remainSec + "s");
    }

    // ============================================================
    // Chat UI
    // ============================================================

    private void initChatUI() {
        if (lvChat == null) return;

        lvChat.setItems(chatItems);
        lvChat.setCellFactory(list -> new ListCell<ChatMessage>() {
            @Override
            protected void updateItem(ChatMessage msg, boolean empty) {
                super.updateItem(msg, empty);
                if (empty || msg == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                boolean mine = msg.getFrom() != null && msg.getFrom().equals(ctx.username);

                Label bubble = new Label(msg.getContent());
                bubble.getStyleClass().add(mine ? "chat-bubble-mine" : "chat-bubble-other");
                bubble.setWrapText(true);
                bubble.setMaxWidth(260);

                HBox row = new HBox(bubble);
                row.setMaxWidth(Double.MAX_VALUE);
                row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

                setText(null);
                setGraphic(row);
            }
        });
    }

    private void appendChat(ChatMessage msg) {
        fx(() -> {
            chatItems.add(msg);
            if (lvChat != null) lvChat.scrollTo(chatItems.size() - 1);
        });
    }

    private void refreshHeader() {
        String mode = aiEnabled ? "OFFLINE vs AI" : "ONLINE PvP";
        String room = (ctx.currentRoomId == null) ? "-" : ctx.currentRoomId;

        if (lbTitle != null) lbTitle.setText("Caro • " + mode);

        if (lbMe != null) lbMe.setText("Bạn: " + (ctx.username == null ? "?" : ctx.username));
        if (lbOpponent != null) lbOpponent.setText("Đối thủ: " + (opponent == null ? "?" : opponent));

        if (lbSub != null) {
            lbSub.setText(
                    "Room=" + room +
                            " | Opponent=" + opponent +
                            " | You=" + myMark +
                            " | Turn=" + (myTurn ? "You" : "Opponent") +
                            " | Board=" + boardSize +
                            (finished ? " | Finished" : "")
            );
        }
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.show(); // non-blocking
    }

    private String buildEndMessage(GameEnd end) {
        if (end.getReason() == GameEndReason.TIMEOUT) {
            if (end.getWinner() == null) return "Kết thúc: Hết giờ.";
            return "Kết thúc: " + end.getWinner() + " thắng do đối thủ hết giờ.";
        }
        if (end.getReason() == GameEndReason.DRAW) return "Kết thúc: Hòa.";
        if (end.getWinner() == null) return "Kết thúc.";
        return "Kết thúc: " + end.getWinner() + " thắng. (" + end.getReason() + ")";
    }


    private void showVictoryEffect() {
        Platform.runLater(() -> {
            // 1. Tạo chữ VICTORY
            Label lblVictory = new Label("VICTORY!");
            lblVictory.setFont(Font.font("Arial", FontWeight.BOLD, 80));
            lblVictory.setTextFill(Color.GOLD);
            lblVictory.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-padding: 20; -fx-background-radius: 20;");

            // Đổ bóng đỏ
            DropShadow ds = new DropShadow();
            ds.setColor(Color.RED);
            ds.setRadius(10);
            lblVictory.setEffect(ds);

            // 2. Thêm vào giao diện (rootPane là fx:id của layout cha trong game.fxml)
            // Nếu bạn chưa có fx:id, hãy thêm vào fxml hoặc gán tạm vào pane chứa bàn cờ
            if (rootPane != null) {
                rootPane.getChildren().add(lblVictory);
            }

            // 3. Hiệu ứng phóng to
            lblVictory.setScaleX(0); 
            lblVictory.setScaleY(0);

            ScaleTransition st = new ScaleTransition(Duration.millis(500), lblVictory);
            st.setFromX(0); st.setFromY(0);
            st.setToX(1.5); st.setToY(1.5);
            st.setCycleCount(2);
            st.setAutoReverse(true);
            
            st.setOnFinished(e -> {
                // Hiện xong 3 giây thì tự biến mất và bật lại nhạc nền
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            if(rootPane != null) rootPane.getChildren().remove(lblVictory);
                            SoundManager.getInstance().playBgm();
                        });
                    }
                }, 3000);
            });
            st.play();
        });
    }
}

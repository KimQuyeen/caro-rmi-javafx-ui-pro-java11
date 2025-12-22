package vn.edu.demo.caro.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.demo.caro.client.core.AppContext;
import vn.edu.demo.caro.client.core.ClientCallbackImpl;
import vn.edu.demo.caro.client.core.WithContext;
import vn.edu.demo.caro.common.model.ChatMessage;
import vn.edu.demo.caro.common.model.Enums.GameEndReason;
import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.model.Enums.PostGameChoice;
import vn.edu.demo.caro.common.model.GameEnd;
import vn.edu.demo.caro.common.model.GameSnapshot;
import vn.edu.demo.caro.common.model.GameStart;
import vn.edu.demo.caro.common.model.GameUpdate;
import vn.edu.demo.caro.common.model.Move;
import vn.edu.demo.caro.common.model.Enums;

import java.time.Instant;

public class GameController implements WithContext {

    private AppContext ctx;
    private ClientCallbackImpl callback;

    @FXML private Label lbTitle;
    @FXML private Label lbSub;
    @FXML private GridPane gridBoard;
    @FXML private ScrollPane spBoard;

    @FXML private ListView<ChatMessage> lvChat;
    @FXML private TextField tfChat;

    @FXML private Button btnUndo;
    @FXML private Button btnRedo;

    @FXML private Label lbTimer;

    private javafx.animation.Timeline timerTimeline;
    private boolean timed;
    private long turnDeadlineMillis;

    private final ObservableList<ChatMessage> chatItems = FXCollections.observableArrayList();

    private int boardSize = 15;

    private StackPane[][] cellPanes;
    private Button[][] cellButtons;
    private Label[][] cellMarks;

    private Mark[][] board;

    private Mark myMark = Mark.EMPTY;
    private boolean myTurn = false;
    private String opponent = "?";
    private boolean finished = false;

    private boolean aiEnabled = false;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        Object cbObj = ctx.stage.getProperties().get("callback");
        if (cbObj instanceof ClientCallbackImpl) {
            this.callback = (ClientCallbackImpl) cbObj;
            this.callback.bindGame(this);
        }

        initChatUI();

        if (btnUndo != null) btnUndo.setDisable(true);
        if (btnRedo != null) btnRedo.setDisable(true);

        aiEnabled = (boolean) ctx.stage.getProperties().getOrDefault("ai.enabled", false);

        if (aiEnabled) {
            setupBoard(15);
            appendChat(new ChatMessage("SYSTEM", "AI", "AI mode chưa được nối trong file này.", Instant.now()));
            refreshHeader();
            return;
        }

        Object pending = ctx.stage.getProperties().remove("pending.gameStart");
        if (pending instanceof GameStart) {
            onGameStart((GameStart) pending);
        } else {
            setupBoard(15);
            appendChat(new ChatMessage("SYSTEM", "ROOM", "Đang chờ đủ 2 người để bắt đầu...", Instant.now()));
            refreshHeader();
        }
    }

    // ===== Legacy/Bridge handlers called by ClientCallbackImpl =====
public void onUndoRequested(String roomId, String from) {
    // Nếu project của bạn trước đây dùng cơ chế "approve undo"
    // thì ở đây bạn mở dialog xác nhận, rồi gọi lobbyService.respondUndo(...)
    // Tạm thời: chỉ hiện thông báo để không crash.
    showAnnouncement(from + " requested UNDO.");
}

public void onRedoRequested(String roomId, String from) {
    showAnnouncement(from + " requested REDO.");
}

public void onRematchRequested(String roomId, String from) {
    showAnnouncement(from + " requested REMATCH.");
}

/**
 * Helper hiển thị thông báo - bạn map vào UI hiện có.
 * Nếu bạn đã có method showAnnouncement/toast/snackbar rồi thì dùng method đó,
 * không cần copy y nguyên.
 */
private void showAnnouncement(String msg) {
    // Ví dụ nếu bạn có label/thông báo nào đó:
    // announcementLabel.setText(msg);

    // Hoặc nếu bạn có Alert/Popup helper:
    // Alerts.info(msg);

    System.out.println("[GameController] " + msg);
}

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

    // ===== server callbacks =====

    public void onGameStart(GameStart start) {
        aiEnabled = false;

        ctx.currentRoomId = start.getRoomId();
        opponent = start.getOpponent();
        myMark = start.getYourMark();
        myTurn = start.isYourTurn();
        finished = false;

        boardSize = start.getBoardSize();
        setupBoard(boardSize);

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
    }

    public void onGameUpdate(GameUpdate update) {
        if (update == null || finished) return;

        Platform.runLater(() -> {
            applyMoveIfPossible(update.getMove(), update.getMark());
            myTurn = ctx.username.equals(update.getNextTurnUser());
            setBoardEnabled(myTurn);
            refreshHeader();
        });
    }

    public void onGameEnd(GameEnd end) {
        if (finished) return;
        finished = true;

        Platform.runLater(() -> {
            stopCountdown();
            setBoardEnabled(false);

            String msg;
            if (end.getReason() == GameEndReason.DRAW) msg = "Kết thúc: Hòa.";
            else if (end.getWinner() == null) msg = "Kết thúc.";
            else msg = "Kết thúc: " + end.getWinner() + " thắng. (" + end.getReason() + ")";

            appendChat(new ChatMessage("SYSTEM", "ROOM:" + end.getRoomId(), msg, Instant.now()));
            refreshHeader();

            // ===== Popup Rematch / Return =====
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Kết thúc ván");
            a.setHeaderText(msg);
            a.setContentText("Rematch?");

            ButtonType rematch = new ButtonType("Request rematch", ButtonBar.ButtonData.YES);
            ButtonType lobby = new ButtonType("Return to lobby", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(rematch, lobby);

            ButtonType chosen = a.showAndWait().orElse(lobby);

            try {
                if (ctx.currentRoomId == null) return;

                if (chosen == rematch) {
                    ctx.lobby.submitPostGameChoice(ctx.currentRoomId, ctx.username, Enums.PostGameChoice.REMATCH);
                } else {
                    ctx.lobby.submitPostGameChoice(ctx.currentRoomId, ctx.username, Enums.PostGameChoice.RETURN);
                }
            } catch (Exception e) {
                showInfo("Lỗi", e.getMessage());
            }
        });
    }

    public void onRoomChat(ChatMessage msg) {
        appendChat(msg);
    }

    // ===== Board =====

    private void setupBoard(int size) {
        if (gridBoard == null) return;

        gridBoard.setManaged(true);
        gridBoard.setVisible(true);

        boardSize = size;
        board = new Mark[size][size];
        cellPanes = new StackPane[size][size];
        cellButtons = new Button[size][size];
        cellMarks = new Label[size][size];

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) board[r][c] = Mark.EMPTY;
        }

        gridBoard.getChildren().clear();
        gridBoard.getColumnConstraints().clear();
        gridBoard.getRowConstraints().clear();

        if (!gridBoard.getStyleClass().contains("game-grid")) {
            gridBoard.getStyleClass().add("game-grid");
        }

        double cellSize = (size <= 12) ? 44 : (size <= 15 ? 38 : 30);

        for (int c = 0; c < size; c++) {
            gridBoard.getColumnConstraints().add(new ColumnConstraints(cellSize));
        }
        for (int r = 0; r < size; r++) {
            gridBoard.getRowConstraints().add(new RowConstraints(cellSize));
        }

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {

                Button btn = new Button("");
                btn.setMinSize(cellSize, cellSize);
                btn.setPrefSize(cellSize, cellSize);
                btn.setMaxSize(cellSize, cellSize);
                btn.getStyleClass().add("game-cell-btn");

                final int rr = r;
                final int cc = c;
                btn.setOnAction(e -> onCellClick(rr, cc));

                Label markLb = new Label("");
                markLb.setMouseTransparent(true);
                markLb.setMinSize(cellSize, cellSize);
                markLb.setPrefSize(cellSize, cellSize);
                markLb.getStyleClass().add("game-cell-label");

                StackPane cell = new StackPane(btn, markLb);
                cellPanes[r][c] = cell;
                cellButtons[r][c] = btn;
                cellMarks[r][c] = markLb;

                gridBoard.add(cell, c, r);
            }
        }

        System.out.println("[Board] Setup complete. Size=" + size + " Total Cells=" + (size * size));
    }

    private void onCellClick(int r, int c) {
        if (finished || aiEnabled) return;
        if (!myTurn) return;
        if (ctx.currentRoomId == null || ctx.currentRoomId.isBlank()) return;
        if (r < 0 || c < 0 || r >= boardSize || c >= boardSize) return;
        if (board == null || board[r][c] != Mark.EMPTY) return;

        // lock local immediately (để tránh click 2 lần)
        myTurn = false;
        setBoardEnabled(false);
        refreshHeader();

        try {
            ctx.lobby.makeMove(ctx.currentRoomId, ctx.username, r, c);
            // không optimistic draw
        } catch (Exception e) {
            showInfo("Không hợp lệ", e.getMessage());
            // KHÔNG tự set myTurn=true nữa (đây là nguyên nhân “đúng lượt mà server bảo sai”)
            // chờ snapshot/update từ server đồng bộ lại
        }
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

    // ===== UI actions =====

    @FXML
    private void onSendChat() {
        String text = (tfChat != null && tfChat.getText() != null) ? tfChat.getText().trim() : "";
        if (text.isBlank()) return;

        try {
            if (ctx.currentRoomId == null) return;

            ctx.lobby.sendRoomChat(ctx.currentRoomId,
                    new ChatMessage(ctx.username, "ROOM:" + ctx.currentRoomId, text, Instant.now()));
            if (tfChat != null) tfChat.clear();

        } catch (Exception e) {
            showInfo("Lỗi", e.getMessage());
        }
    }

    @FXML
    private void onUndo() {
        if (aiEnabled || finished) return;
        try { ctx.lobby.requestUndo(ctx.currentRoomId, ctx.username); }
        catch (Exception e) { showInfo("Lỗi", e.getMessage()); }
    }

    @FXML
    private void onRedo() {
        if (aiEnabled || finished) return;
        try { ctx.lobby.requestRedo(ctx.currentRoomId, ctx.username); }
        catch (Exception e) { showInfo("Lỗi", e.getMessage()); }
    }

    @FXML
    private void onResign() {
        if (finished || aiEnabled) return;
        try { ctx.lobby.resign(ctx.currentRoomId, ctx.username); }
        catch (Exception e) { showInfo("Lỗi", e.getMessage()); }
    }

    @FXML
    private void onBackToMain() {
        stopCountdown();
        try {
            if (!aiEnabled && ctx.currentRoomId != null) {
                ctx.lobby.leaveRoom(ctx.username, ctx.currentRoomId);
            }
        } catch (Exception ignored) {}

        ctx.currentRoomId = null;
        ctx.sceneManager.showMain();
    }

    // ===== snapshot apply =====
    public void applySnapshot(GameSnapshot snap) {
        if (snap == null) return;

        Platform.runLater(() -> {
            if (board == null || cellButtons == null || cellMarks == null || snap.getBoardSize() != boardSize) {
                boardSize = snap.getBoardSize();
                setupBoard(boardSize);
            }

            Mark[][] b = snap.getBoard();
            if (b == null) return;

            for (int r = 0; r < boardSize; r++) {
                for (int c = 0; c < boardSize; c++) {
                    Mark m = b[r][c];
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

            myTurn = ctx.username != null && ctx.username.equals(snap.getTurn());
            setBoardEnabled(!finished && myTurn);
            refreshHeader();

            // timer state (PHẢI chạy trên FX thread)
            timed = snap.isTimed();
            turnDeadlineMillis = snap.getTurnDeadlineMillis();
            startOrStopCountdown();
        });
    }

    private void startOrStopCountdown() {
        stopCountdown();

        if (!timed || turnDeadlineMillis <= 0) {
            if (lbTimer != null) lbTimer.setText("No timer");
            return;
        }

        timerTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0), e -> updateTimerLabel()),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1))
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

        if (lbTimer != null) {
            lbTimer.setText((myTurn ? "Your turn" : "Opponent") + " • " + remainSec + "s");
        }
    }

    private void appendChat(ChatMessage msg) {
        Platform.runLater(() -> {
            chatItems.add(msg);
            if (lvChat != null) lvChat.scrollTo(chatItems.size() - 1);
        });
    }

    private void refreshHeader() {
        String mode = aiEnabled ? "OFFLINE vs AI" : "ONLINE PvP";
        String room = (ctx.currentRoomId == null) ? "-" : ctx.currentRoomId;

        if (lbTitle != null) lbTitle.setText("Caro • " + mode);
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
        a.showAndWait();
    }

    public void onUndoResult(String roomId, boolean accepted, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
    }

    public void onRedoResult(String roomId, boolean accepted, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
    }

    public void onReturnToLobby(String roomId, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
        Platform.runLater(this::onBackToMain);
    }
}

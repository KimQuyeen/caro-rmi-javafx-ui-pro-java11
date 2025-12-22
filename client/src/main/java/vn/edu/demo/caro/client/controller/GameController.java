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
import vn.edu.demo.caro.common.model.GameEnd;
import vn.edu.demo.caro.common.model.GameStart;
import vn.edu.demo.caro.common.model.GameUpdate;
import vn.edu.demo.caro.common.model.Move;

import java.time.Instant;

public class GameController implements WithContext {

    private AppContext ctx;
    private ClientCallbackImpl callback;

    // ===== FXML =====
    @FXML private Label lbTitle;
    @FXML private Label lbSub;
    @FXML private GridPane gridBoard;
    @FXML private ScrollPane spBoard;

    @FXML private ListView<ChatMessage> lvChat;
    @FXML private TextField tfChat;

    @FXML private Button btnUndo;
    @FXML private Button btnRedo;

    // ===== chat state =====
    private final ObservableList<ChatMessage> chatItems = FXCollections.observableArrayList();

    // ===== board state =====
    private int boardSize = 15;

    // Mỗi ô: StackPane chứa Button (click) + Label (vẽ X/O)
    private StackPane[][] cellPanes;
    private Button[][] cellButtons;
    private Label[][] cellMarks;

    private Mark[][] board;

    private Mark myMark = Mark.EMPTY;
    private boolean myTurn = false;
    private String opponent = "?";
    private boolean finished = false;

    private boolean aiEnabled = false; // nếu chưa dùng AI thì để false

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

    // ===== callbacks from server =====

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

        refreshHeader();
    }

    public void onGameUpdate(GameUpdate update) {
        if (update == null || finished) return;

        Platform.runLater(() -> {
            applyMoveIfPossible(update.getMove(), update.getMark());
            myTurn = ctx.username.equals(update.getNextTurnUser());
            refreshHeader();
        });

        try {
            System.out.println("[UPDATE] mark=" + update.getMark()
                    + " by=" + update.getMove().getBy()
                    + " next=" + update.getNextTurnUser()
                    + " r=" + update.getMove().getRow()
                    + " c=" + update.getMove().getCol());
        } catch (Exception ignored) {}
    }

    public void onGameEnd(GameEnd end) {
        if (finished) return;
        finished = true;

        String msg;
        if (end.getReason() == GameEndReason.DRAW) msg = "Kết thúc: Hòa.";
        else if (end.getWinner() == null) msg = "Kết thúc.";
        else msg = "Kết thúc: " + end.getWinner() + " thắng. (" + end.getReason() + ")";

        appendChat(new ChatMessage("SYSTEM", "ROOM:" + end.getRoomId(), msg, Instant.now()));
        refreshHeader();
    }

    public void onRoomChat(ChatMessage msg) {
        appendChat(msg);
    }

    // ===== board =====

    private void setupBoard(int size) {
    if (gridBoard == null) return;

    gridBoard.setManaged(true);
    gridBoard.setVisible(true);
    
    // Reset Board
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

    // --- SỬA Ở ĐÂY: Dùng Class CSS thay vì setStyle inline ---
    gridBoard.getStyleClass().add("game-grid"); 
    // gridBoard.setGridLinesVisible(true); // Bật lên nếu muốn debug khung

    // Tính kích thước ô
    double cellSize = (size <= 12) ? 44 : (size <= 15 ? 38 : 30);

    // Tạo Constraints cho cột/dòng
    for (int c = 0; c < size; c++) {
        ColumnConstraints cc = new ColumnConstraints(cellSize);
        gridBoard.getColumnConstraints().add(cc);
    }
    for (int r = 0; r < size; r++) {
        RowConstraints rc = new RowConstraints(cellSize);
        gridBoard.getRowConstraints().add(rc);
    }

    // Vẽ ô
    for (int r = 0; r < size; r++) {
        for (int c = 0; c < size; c++) {
            
            // 1) Button
            Button btn = new Button(""); // Text rỗng
            btn.setMinSize(cellSize, cellSize);
            btn.setPrefSize(cellSize, cellSize);
            btn.setMaxSize(cellSize, cellSize);
            
            // --- SỬA: Dùng class CSS ---
            btn.getStyleClass().add("game-cell-btn");
            
            final int rr = r;
            final int cc = c;
            btn.setOnAction(e -> onCellClick(rr, cc));

            // 2) Label hiển thị
            Label markLb = new Label("");
            markLb.setMouseTransparent(true);
            markLb.setMinSize(cellSize, cellSize);
            markLb.setPrefSize(cellSize, cellSize);
            
            // --- SỬA: Dùng class CSS ---
            markLb.getStyleClass().add("game-cell-label");

            // 3) StackPane
            StackPane cell = new StackPane(btn, markLb);
            cellPanes[r][c] = cell;
            cellButtons[r][c] = btn;
            cellMarks[r][c] = markLb;

            gridBoard.add(cell, c, r);
        }
    }
    
    // Log debug
    System.out.println("[Board] Setup complete. Size=" + size + " Total Cells=" + (size*size));
}
    private void onCellClick(int r, int c) {
        if (finished || aiEnabled) return;

        if (!myTurn) return;
        if (ctx.currentRoomId == null || ctx.currentRoomId.isBlank()) return;
        if (r < 0 || c < 0 || r >= boardSize || c >= boardSize) return;
        if (board == null || board[r][c] != Mark.EMPTY) return;

        try {
            System.out.println("[CLICK] room=" + ctx.currentRoomId + " user=" + ctx.username
                    + " r=" + r + " c=" + c + " myTurn=" + myTurn + " myMark=" + myMark);

            // 1) gọi server
            ctx.lobby.makeMove(ctx.currentRoomId, ctx.username, r, c);

            // 2) optimistic UI: vẽ luôn
            applyMoveIfPossible(new Move(r, c, 0, ctx.username), myMark);

            // 3) khóa lượt local
            myTurn = false;
            refreshHeader();

        } catch (Exception e) {
            showInfo("Không hợp lệ", e.getMessage());
        }
    }

   private void applyMoveIfPossible(Move mv, Mark mark) {
    if (mv == null || mark == null) return;

    int r = mv.getRow();
    int c = mv.getCol();
    if (r < 0 || c < 0 || r >= boardSize || c >= boardSize) return;

    if (board[r][c] != Mark.EMPTY) return;

    board[r][c] = mark;

    Label markLb = cellMarks[r][c];
    Button btn = cellButtons[r][c];

    if (markLb != null) {
        markLb.setText(mark == Mark.X ? "X" : "O");
        
        // --- SỬA: Thêm class màu sắc ---
        markLb.getStyleClass().removeAll("mark-x", "mark-o"); // Xóa class cũ nếu có
        if (mark == Mark.X) {
            markLb.getStyleClass().add("mark-x");
        } else {
            markLb.getStyleClass().add("mark-o");
        }
    }

    if (btn != null) {
        btn.setMouseTransparent(true);
        btn.setOnAction(null);
    }
    
    System.out.println("[DRAW] r=" + r + " c=" + c + " text=" + (mark == Mark.X ? "X" : "O"));
}

    // ===== UI actions =====

    @FXML
    private void onSendChat() {
        String text = (tfChat != null && tfChat.getText() != null) ? tfChat.getText().trim() : "";
        if (text.isBlank()) return;

        if (aiEnabled) {
            appendChat(new ChatMessage(ctx.username, "AI", text, Instant.now()));
            if (tfChat != null) tfChat.clear();
            return;
        }

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
        try {
            if (!aiEnabled && ctx.currentRoomId != null) {
                ctx.lobby.leaveRoom(ctx.username, ctx.currentRoomId);
            }
        } catch (Exception ignored) {}

        ctx.currentRoomId = null;
        ctx.sceneManager.showMain();
    }

    // ===== helpers =====

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

    // ===== stubs để callback compile =====
    public void onUndoRequested(String roomId, String from) {}
    public void onRedoRequested(String roomId, String from) {}

    public void onUndoResult(String roomId, boolean accepted, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
    }

    public void onRedoResult(String roomId, boolean accepted, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
    }

    public void onRematchRequested(String roomId, String from) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, "Đối thủ đã request rematch.", Instant.now()));
    }

    public void onReturnToLobby(String roomId, String message) {
        appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId, message, Instant.now()));
        Platform.runLater(this::onBackToMain);
    }
}

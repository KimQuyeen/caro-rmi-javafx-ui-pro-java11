package vn.edu.demo.caro.client.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.edu.demo.caro.client.core.AppContext;
import vn.edu.demo.caro.client.core.ClientCallbackImpl;
import vn.edu.demo.caro.client.core.WithContext;
import vn.edu.demo.caro.common.model.ChatMessage;
import vn.edu.demo.caro.common.model.Enums;
import vn.edu.demo.caro.common.model.Enums.GameEndReason;
import vn.edu.demo.caro.common.model.Enums.Mark;
import vn.edu.demo.caro.common.model.GameEnd;
import vn.edu.demo.caro.common.model.GameSnapshot;
import vn.edu.demo.caro.common.model.GameStart;
import vn.edu.demo.caro.common.model.GameUpdate;
import vn.edu.demo.caro.common.model.Move;
import vn.edu.demo.caro.common.model.UserPublicProfile;

import java.time.Instant;
import java.util.Optional;

public class GameController implements WithContext {

    private AppContext ctx;
    private ClientCallbackImpl callback;
@FXML private Label lbMe;
@FXML private Label lbOpponent;

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

    // ===== Post-game / Rematch state =====
    private volatile boolean opponentRequestedRematch = false;
    private volatile boolean waitingRematchDecision = false;

    private Alert endGameAlert;         // popup "Kết thúc ván"
    private Stage rematchStage;         // popup rematch custom
    private volatile boolean suppressPostGameChoice = false;

    @FXML
private void onOpponentClicked() {
    if (aiEnabled) return;

    String target = (opponent == null) ? "" : opponent.trim();
    if (target.isBlank() || "?".equals(target)) return;
    if (ctx.username != null && ctx.username.equalsIgnoreCase(target)) return;
    if ("AI".equalsIgnoreCase(target)) return;

    showOpponentProfilePopup(target);
}


private void showOpponentProfilePopup(String target) {
    final Dialog<Void> dlg = new Dialog<>();
    dlg.setTitle("Thông tin người chơi");
    dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

    final Label lbName = new Label("Tên: " + target);
    final Label lbPlayed = new Label("Số ván đã chơi: ...");
    final Label lbWLD = new Label("Thắng/Thua/Hòa: ...");
    final Label lbWinRate = new Label("Tỉ lệ thắng: ...");
    final Label lbElo = new Label("Điểm: ...");
    final Label lbRank = new Label("Thứ hạng: ...");
    final Label lbFriend = new Label("Trạng thái bạn bè: ...");

    // Nút gửi lời mời (khi NOT_FRIEND)
    final Button btnAdd = new Button("Add friend");
    btnAdd.setDisable(true);

    // Nút chấp nhận / từ chối (khi INCOMING_PENDING)
    final Button btnAccept = new Button("Chấp nhận");
    final Button btnDecline = new Button("Từ chối");
    btnAccept.setVisible(false);
    btnDecline.setVisible(false);

    final VBox box = new VBox(10,
            lbName, lbPlayed, lbWLD, lbWinRate, lbElo, lbRank, lbFriend,
            btnAdd, btnAccept, btnDecline
    );
    box.setStyle("-fx-padding: 16; -fx-min-width: 420;");
    dlg.getDialogPane().setContent(box);

    // Helper reset UI buttons theo trạng thái
    Runnable resetButtons = () -> {
        btnAdd.setVisible(true);
        btnAdd.setDisable(true);
        btnAdd.setText("Add friend");
        btnAdd.setOnAction(null);

        btnAccept.setVisible(false);
        btnDecline.setVisible(false);
        btnAccept.setDisable(false);
        btnDecline.setDisable(false);
        btnAccept.setOnAction(null);
        btnDecline.setOnAction(null);
    };

    // Load dữ liệu từ server
    new Thread(() -> {
        try {
            final String me = ctx.username; // đổi theo code bạn
            final UserPublicProfile p = ctx.lobby.getUserPublicProfile(me, target);

            Platform.runLater(() -> {
                lbName.setText("Tên: " + p.getUsername());
                lbPlayed.setText("Số ván đã chơi: " + p.getGamesPlayed());
                lbWLD.setText("Thắng/Thua/Hòa: " + p.getWins() + "/" + p.getLosses() + "/" + p.getDraws());
                lbWinRate.setText(String.format("Tỉ lệ thắng: %.1f%%", p.getWinRate()));
                lbElo.setText("Điểm: " + p.getElo());
                lbRank.setText("Thứ hạng: #" + p.getRank());

                resetButtons.run();

                UserPublicProfile.FriendStatus st = p.getFriendStatus();
                switch (st) {
                    case FRIEND:
                        lbFriend.setText("Trạng thái bạn bè: Đã kết bạn");
                        btnAdd.setVisible(false);
                        break;

                    case OUTGOING_PENDING:
                        lbFriend.setText("Trạng thái bạn bè: Đã gửi lời mời (đang chờ)");
                        btnAdd.setText("Đã gửi lời mời");
                        btnAdd.setDisable(true);
                        break;

                    case INCOMING_PENDING:
                        lbFriend.setText("Trạng thái bạn bè: Đối thủ đã gửi lời mời cho bạn");
                        btnAdd.setVisible(false);

                        btnAccept.setVisible(true);
                        btnDecline.setVisible(true);

                        // IMPORTANT: respondFriendRequest(from, to, accept)
                        // from = người gửi lời mời = target
                        // to   = người nhận = me
                        btnAccept.setOnAction(ev -> {
                            btnAccept.setDisable(true);
                            btnDecline.setDisable(true);

                            new Thread(() -> {
                                try {
                                    ctx.lobby.respondFriendRequest(target, me, true);

                                    // Refresh lại profile để update UI ngay
                                    UserPublicProfile p2 = ctx.lobby.getUserPublicProfile(me, target);
                                    Platform.runLater(() -> {
                                        lbFriend.setText("Trạng thái bạn bè: Đã kết bạn");
                                        btnAccept.setVisible(false);
                                        btnDecline.setVisible(false);
                                        btnAdd.setVisible(false);
                                    });
                                } catch (Exception ex) {
                                    Platform.runLater(() -> showInfo("Lỗi", ex.getMessage()));
                                }
                            }).start();
                        });

                        btnDecline.setOnAction(ev -> {
                            btnAccept.setDisable(true);
                            btnDecline.setDisable(true);

                            new Thread(() -> {
                                try {
                                    ctx.lobby.respondFriendRequest(target, me, false);

                                    // Sau khi từ chối, refresh lại profile
                                    UserPublicProfile p2 = ctx.lobby.getUserPublicProfile(me, target);
                                    Platform.runLater(() -> {
                                        // thường sẽ về NOT_FRIEND (vì DENIED không còn PENDING)
                                        lbFriend.setText("Trạng thái bạn bè: Chưa kết bạn");
                                        btnAccept.setVisible(false);
                                        btnDecline.setVisible(false);

                                        btnAdd.setVisible(true);
                                        btnAdd.setDisable(false);
                                        btnAdd.setText("Add friend");
                                        btnAdd.setOnAction(e2 -> sendFriendRequestFromPopup(target, btnAdd));
                                    });
                                } catch (Exception ex) {
                                    Platform.runLater(() -> showInfo("Lỗi", ex.getMessage()));
                                }
                            }).start();
                        });
                        break;

                    case NOT_FRIEND:
                    default:
                        lbFriend.setText("Trạng thái bạn bè: Chưa kết bạn");
                        btnAdd.setDisable(false);
                        btnAdd.setOnAction(e -> sendFriendRequestFromPopup(target, btnAdd));
                        break;
                }
            });

        } catch (Exception ex) {
            Platform.runLater(() -> showInfo("Lỗi", ex.getMessage()));
        }
    }).start();

    dlg.show();
}


private void sendFriendRequestFromPopup(String target, Button btnAdd) {
    btnAdd.setDisable(true);
    btnAdd.setText("Đang gửi...");

    new Thread(() -> {
        try {
            String me = ctx.username; // đổi theo code bạn
            ctx.lobby.sendFriendRequestByName(me, target); // đổi theo code bạn

            Platform.runLater(() -> btnAdd.setText("Đã gửi lời mời"));
        } catch (Exception ex) {
            Platform.runLater(() -> {
                btnAdd.setDisable(false);
                btnAdd.setText("Add friend");
                showInfo("Lỗi", ex.getMessage());
            });
        }
    }).start();
}

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

    // ============================================================
    // Server callback handlers (called via ClientCallbackImpl)
    // ============================================================

    public void onUndoRequested(String roomId, String from) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Undo request");
            a.setHeaderText(from + " yêu cầu Undo");
            a.setContentText("Bạn có đồng ý không?");

            ButtonType accept = new ButtonType("Accept", ButtonBar.ButtonData.YES);
            ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(accept, reject);

            ButtonType chosen = a.showAndWait().orElse(reject);

            try {
                ctx.lobby.respondUndo(roomId, ctx.username, chosen == accept);
            } catch (Exception e) {
                showInfo("Lỗi", e.getMessage());
            }
        });
    }

    public void onRedoRequested(String roomId, String from) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Đề nghị hòa");
            a.setHeaderText(from + " đề nghị hòa");
            a.setContentText("Bạn có đồng ý không?");

            ButtonType accept = new ButtonType("Accept", ButtonBar.ButtonData.YES);
            ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(accept, reject);

            ButtonType chosen = a.showAndWait().orElse(reject);

            try {
                ctx.lobby.respondRedo(roomId, ctx.username, chosen == accept);
            } catch (Exception e) {
                showInfo("Lỗi", e.getMessage());
            }
        });
    }

    public void onAnnouncement(String msg) {
        appendChat(new ChatMessage("SYSTEM", "ANNOUNCE", msg, Instant.now()));
    }

    public void onRematchRequested(String roomId, String from) {
        Platform.runLater(() -> {
            // mark: opponent asked rematch
            opponentRequestedRematch = true;

            // close endgame popup if open
            suppressPostGameChoice = true;
            closeEndGameAlertIfAny();

            // if I already requested, no need to show prompt
            if (waitingRematchDecision) {
                appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId,
                        from + " cũng muốn Rematch. Đang chờ server bắt đầu ván mới...", Instant.now()));
                return;
            }

            showRematchPromptStage(roomId, from);
        });
    }

    public void onGameStart(GameStart start) {
    aiEnabled = false;

    // reset post-game flags each new match
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

    // ---- NEW: set header labels ----
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
}


    public void onGameUpdate(GameUpdate update) {
        if (update == null || finished) return;

        Platform.runLater(() -> {
            applyMoveIfPossible(update.getMove(), update.getMark());
            myTurn = ctx.username != null && ctx.username.equals(update.getNextTurnUser());
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

            // reset suppress each end
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

            // Delay show popup to avoid IllegalStateException during layout/animation
           PauseTransition pt = new PauseTransition(Duration.millis(150));
pt.setOnFinished(ev -> Platform.runLater(() -> {
    if (waitingRematchDecision || opponentRequestedRematch) return;
    showPostGameChoicePopup(msg);
}));
pt.play();

        });
    }

    public void onRoomChat(ChatMessage msg) {
        appendChat(msg);
    }

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

            String t = snap.getTurn();
            if (t == null || t.isBlank()) {
                myTurn = false;
                finished = false; // WAITING state
                setBoardEnabled(false);
                if (lbTimer != null) lbTimer.setText("Waiting for opponent...");
            } else {
                myTurn = ctx.username != null && ctx.username.equals(t);
                setBoardEnabled(!finished && myTurn);
            }

            timed = snap.isTimed();
            turnDeadlineMillis = snap.getTurnDeadlineMillis();
            startOrStopCountdown();

            refreshHeader();
        });
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

    // ============================================================
    // UI actions
    // ============================================================

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

    // ============================================================
    // Board
    // ============================================================

    private void setupBoard(int size) {
        if (gridBoard == null) return;

        gridBoard.setManaged(true);
        gridBoard.setVisible(true);

        boardSize = size;
        board = new Mark[size][size];
        cellPanes = new StackPane[size][size];
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
    }

    private void onCellClick(int r, int c) {
        if (finished || aiEnabled) return;
        if (!myTurn) return;
        if (ctx.currentRoomId == null || ctx.currentRoomId.isBlank()) return;
        if (r < 0 || c < 0 || r >= boardSize || c >= boardSize) return;
        if (board == null || board[r][c] != Mark.EMPTY) return;

        myTurn = false;
        setBoardEnabled(false);
        refreshHeader();

        try {
            ctx.lobby.makeMove(ctx.currentRoomId, ctx.username, r, c);
        } catch (Exception e) {
            showInfo("Không hợp lệ", e.getMessage());
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

    // Non-blocking: xử lý kết quả sau khi dialog đóng
    a.setResultConverter(bt -> bt);

    a.setOnHidden(ev -> {
        // dialog đã đóng => lấy kết quả
        ButtonType chosen = a.getResult();
        if (endGameAlert == a) endGameAlert = null;

        if (suppressPostGameChoice) return;
        if (chosen == null) return;
        if (chosen.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) return;

        try {
            if (ctx.currentRoomId == null) return;

            if (chosen == rematch) {
                waitingRematchDecision = true;
                ctx.lobby.submitPostGameChoice(ctx.currentRoomId, ctx.username, Enums.PostGameChoice.REMATCH);
                appendChat(new ChatMessage("SYSTEM", "ROOM:" + ctx.currentRoomId,
                        "Đã gửi yêu cầu Rematch. Đang chờ đối thủ...", Instant.now()));
            } else if (chosen == lobby) {
                ctx.lobby.submitPostGameChoice(ctx.currentRoomId, ctx.username, Enums.PostGameChoice.RETURN);
            }
        } catch (Exception e) {
            showInfo("Lỗi", e.getMessage());
        }
    });

    // Quan trọng: show() (KHÔNG showAndWait)
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

        btnYes.setDefaultButton(true);
        btnNo.setCancelButton(true);

        btnYes.setOnAction(e -> {
            try {
                waitingRematchDecision = true;
                ctx.lobby.submitPostGameChoice(roomId, ctx.username, Enums.PostGameChoice.REMATCH);
                appendChat(new ChatMessage("SYSTEM", "ROOM:" + roomId,
                        "Bạn đã đồng ý Rematch. Đang chờ server bắt đầu ván mới...", Instant.now()));
            } catch (Exception ex) {
                showInfo("Lỗi", ex.getMessage());
            } finally {
                opponentRequestedRematch = false;
                st.close();
            }
        });

        btnNo.setOnAction(e -> {
            try {
                ctx.lobby.submitPostGameChoice(roomId, ctx.username, Enums.PostGameChoice.RETURN);
            } catch (Exception ex) {
                showInfo("Lỗi", ex.getMessage());
            } finally {
                opponentRequestedRematch = false;
                st.close();
            }
        });

        HBox buttons = new HBox(10, btnYes, btnNo);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, header, body, buttons);
        root.setStyle("-fx-padding: 16; -fx-background-color: white;");

        st.setScene(new Scene(root));
        st.setOnHidden(e -> {
            if (rematchStage == st) rematchStage = null;
        });

        st.showAndWait();
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

        if (lbTimer != null) {
            lbTimer.setText((myTurn ? "Your turn" : "Opponent") + " • " + remainSec + "s");
        }
    }

    // ============================================================
    // Misc UI helpers
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
        Platform.runLater(() -> {
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
    Platform.runLater(() -> {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.show(); // không showAndWait
    });
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
}

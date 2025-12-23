package vn.edu.demo.caro.client.controller.view;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import vn.edu.demo.caro.client.core.*;
import vn.edu.demo.caro.common.model.Enums;
import vn.edu.demo.caro.common.model.RoomInfo;
import vn.edu.demo.caro.common.model.RoomCreateRequest;
import java.util.stream.Collectors;

import java.util.Collections;

public class RoomsViewController implements WithContext {

    private AppContext ctx;

    @FXML private ListView<RoomInfo> lvRooms;
    @FXML private TextField tfRoomName;
    @FXML private Label lbOnline;

    @FXML private ComboBox<Integer> cbBoardSize;

    @FXML private CheckBox chkTimed;
    @FXML private ComboBox<Integer> cbTimeSeconds;

    @FXML private CheckBox chkPassword;
    @FXML private PasswordField pfRoomPassword;

    @FXML private CheckBox chkBlockTwoEnds;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        setupRoomListCellFactory();

        Object cb = ctx.stage.getProperties().get("callback");
        if (cb instanceof ClientCallbackImpl) ((ClientCallbackImpl) cb).bindRooms(this);

        cbBoardSize.getItems().setAll(10, 12, 15, 20);
        cbBoardSize.getSelectionModel().select(Integer.valueOf(15));

        cbTimeSeconds.getItems().setAll(15, 30, 60, 120, 300);
        cbTimeSeconds.getSelectionModel().select(Integer.valueOf(60));

        cbTimeSeconds.disableProperty().bind(chkTimed.selectedProperty().not());
        pfRoomPassword.disableProperty().bind(chkPassword.selectedProperty().not());

        refreshRooms();
        refreshOnline();
    }

    @FXML
private void onCreateRoom() {
    try {
        String roomName = tfRoomName.getText() == null ? "" : tfRoomName.getText().trim();
        if (roomName.isEmpty()) {
            showInfo("Lỗi", "Tên phòng không được trống.");
            return;
        }

        RoomCreateRequest req = new RoomCreateRequest();
        req.setRoomName(roomName);

        Integer size = cbBoardSize.getValue();
        req.setBoardSize(size == null ? 15 : size);
        req.setBlockTwoEnds(chkBlockTwoEnds.isSelected());

        req.setTimed(chkTimed.isSelected());
        Integer sec = cbTimeSeconds.getValue();
        req.setTimeLimitSeconds(req.isTimed() ? (sec == null ? 60 : sec) : 0);

        req.setPasswordEnabled(chkPassword.isSelected());
        String pw = pfRoomPassword.getText() == null ? "" : pfRoomPassword.getText();
        if (req.isPasswordEnabled() && pw.trim().isEmpty()) {
            showInfo("Lỗi", "Bạn đã bật mật khẩu nhưng chưa nhập mật khẩu.");
            return;
        }
        req.setPassword(req.isPasswordEnabled() ? pw : null);

        // chạy nền cho chắc (RMI có thể block)
        ctx.io().execute(() -> {
            try {
                String roomId = ctx.lobby.createRoom(ctx.username, req);
                ctx.currentRoomId = roomId;

                Platform.runLater(() ->
                    showInfo("OK", "Tạo phòng thành công. Đang chờ người chơi thứ 2...")
                );

            } catch (Exception e) {
                Platform.runLater(() -> showInfo("Lỗi", e.getMessage()));
            }
        });

    } catch (Exception e) {
        showInfo("Lỗi", e.getMessage());
    }
}


    private String askPasswordIfNeeded(RoomInfo room) {
        if (room == null) return null;
        if (!room.isPasswordEnabled()) return null;

        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Mật khẩu phòng");
        dlg.setHeaderText("Phòng \"" + room.getName() + "\" có mật khẩu.");
        dlg.setContentText("Nhập mật khẩu:");

        return dlg.showAndWait()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

@FXML
private void onJoinSelected() {
    RoomInfo room = lvRooms.getSelectionModel().getSelectedItem();
    if (room == null) return;

    if (room.getStatus() != Enums.RoomStatus.WAITING) {
        showInfo("Không thể vào", "Phòng đã bắt đầu hoặc đã đóng.");
        return;
    }
    if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
        showInfo("Không thể vào", "Phòng đã đầy.");
        return;
    }

    String pw = askPasswordIfNeeded(room);
    if (room.isPasswordEnabled() && (pw == null || pw.isBlank())) {
        showInfo("Không thể vào", "Bạn chưa nhập mật khẩu.");
        return;
    }

    final String roomId = room.getId();   // hoặc room.getRoomId() đều được vì bạn có alias
    final String user = ctx.username;

    lvRooms.setDisable(true);

    ctx.io().execute(() -> {
        try {
            boolean ok = ctx.lobby.joinRoom(user, roomId, pw);
            if (!ok) {
                Platform.runLater(() -> showInfo("Không thể vào", "Phòng đã đầy hoặc đã bắt đầu."));
                return;
            }

            ctx.currentRoomId = roomId;

            // KHÔNG chuyển scene ở đây.
            // Khi đủ 2 người, server sẽ callback onGameStarted => ClientCallbackImpl sẽ showGame().
            Platform.runLater(() -> showInfo("Đã vào phòng", "Đang chờ đủ 2 người để bắt đầu..."));

        } catch (Exception e) {
            Platform.runLater(() -> showInfo("Join thất bại", e.getMessage()));
        } finally {
            Platform.runLater(() -> lvRooms.setDisable(false));
        }
    });
}




    @FXML
    private void onQuickPlay() {
        try {
            ctx.lobby.quickPlay(ctx.username);
            // QuickPlay cũng không chuyển scene; chờ onGameStarted
            showInfo("QuickPlay", "Đang tìm phòng phù hợp. Khi đủ 2 người sẽ tự vào trận...");
        } catch (Exception e) {
            showInfo("Lỗi", e.getMessage());
        }
    }

  public void refreshRooms() {
    if (lvRooms == null) return;

    java.util.List<RoomInfo> open = ctx.rooms.stream()
            .filter(r -> r != null)
            .filter(r -> r.getStatus() == Enums.RoomStatus.WAITING)
            .filter(r -> r.getCurrentPlayers() < r.getMaxPlayers())
            .collect(java.util.stream.Collectors.toList());

    lvRooms.getItems().setAll(open);
}


    public void refreshOnline() {
        if (lbOnline != null) lbOnline.setText("Online: " + ctx.onlineUsers.size());
    }

//     public void setRooms(java.util.List<RoomInfo> rooms) {
//     if (lvRooms == null) return;
//     if (rooms == null) rooms = Collections.emptyList();

//     // Chỉ hiển thị phòng trống đúng nghĩa
//     java.util.List<RoomInfo> open = rooms.stream()
//             .filter(r -> r.getStatus() == Enums.RoomStatus.WAITING)
//             .filter(r -> r.getCurrentPlayers() < r.getMaxPlayers())
//             .collect(Collectors.toList());   // Java 11 OK

//     lvRooms.getItems().setAll(open);        // setAll(Collection) OK
// }


    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.showAndWait();
    }

    private void setupRoomListCellFactory() {
        lvRooms.setCellFactory(list -> new ListCell<RoomInfo>() {
            @Override
            protected void updateItem(RoomInfo r, boolean empty) {
                super.updateItem(r, empty);

                if (empty || r == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String title = String.format("%s  [%s]", safe(r.getName()), safe(String.valueOf(r.getStatus())));
                String meta = String.format("Chủ phòng: %s   |   Người chơi: %d/%d",
                        safe(r.getOwner()), r.getCurrentPlayers(), r.getMaxPlayers());

                String rules = buildRulesText(r);

                Label lbTitle = new Label(title);
                Label lbMeta = new Label(meta);
                Label lbRules = new Label(rules);
                lbRules.setWrapText(true);

                VBox box = new VBox(2, lbTitle, lbMeta, lbRules);
                setText(null);
                setGraphic(box);
            }
        });
    }

    private String buildRulesText(RoomInfo r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bàn: ").append(r.getBoardSize()).append("x").append(r.getBoardSize());
        sb.append(" | Chặn 2 đầu: ").append(r.isBlockTwoEnds() ? "Có" : "Không");
        sb.append(" | Thời gian: ").append(r.isTimed() ? (r.getTimeLimitSeconds() + "s/lượt") : "Không");
        sb.append(" | Mật khẩu: ").append(r.isPasswordEnabled() ? "Có" : "Không");
        return sb.toString();
    }

    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }
}

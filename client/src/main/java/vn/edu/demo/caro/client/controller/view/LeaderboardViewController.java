package vn.edu.demo.caro.client.controller.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import vn.edu.demo.caro.client.core.*;
import vn.edu.demo.caro.common.model.UserProfile;
import vn.edu.demo.caro.common.model.UserPublicProfile;
import vn.edu.demo.caro.common.model.UserPublicProfile.FriendStatus;

public class LeaderboardViewController implements WithContext {

    private AppContext ctx;

    @FXML private TableView<UserProfile> table;
    @FXML private TableColumn<UserProfile, String> colUser;
    @FXML private TableColumn<UserProfile, Integer> colElo;
    @FXML private TableColumn<UserProfile, Integer> colW;
    @FXML private TableColumn<UserProfile, Integer> colL;
    @FXML private TableColumn<UserProfile, Integer> colD;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        Object cb = ctx.stage.getProperties().get("callback");
        if (cb instanceof ClientCallbackImpl) ((ClientCallbackImpl) cb).bindLeaderboard(this);

        // 1. Cấu hình cột
        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colElo.setCellValueFactory(new PropertyValueFactory<>("elo"));
        colW.setCellValueFactory(new PropertyValueFactory<>("wins"));
        colL.setCellValueFactory(new PropertyValueFactory<>("losses"));
        colD.setCellValueFactory(new PropertyValueFactory<>("draws"));

        // 2. Thêm sự kiện Double Click vào dòng
        table.setRowFactory(tv -> {
            TableRow<UserProfile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    UserProfile rowData = row.getItem();
                    onUserClicked(rowData.getUsername());
                }
            });
            return row;
        });

        refresh();
    }

    public void refresh() { 
        if (table != null) table.setItems(FXCollections.observableArrayList(ctx.leaderboard)); 
    }

    // ========================================================
    // LOGIC XỬ LÝ CLICK & KẾT BẠN
    // ========================================================

    private void onUserClicked(String targetUser) {
        // Không xem chính mình
        if (targetUser == null || (ctx.username != null && ctx.username.equals(targetUser))) return;

        // Gọi Server lấy thông tin chi tiết
        runRemote("Lấy thông tin", () -> {
            try {
                // [FIX LỖI 1] Thêm try-catch để bắt RemoteException
                UserPublicProfile profile = ctx.lobby.getUserPublicProfile(ctx.username, targetUser);
                fx(() -> showUserProfileDialog(profile));
            } catch (Exception e) {
                e.printStackTrace();
                fx(() -> showInfo("Lỗi", "Không lấy được thông tin: " + e.getMessage()));
            }
        });
    }

    private void showUserProfileDialog(UserPublicProfile p) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông tin người chơi");
        dialog.setHeaderText("Hồ sơ: " + p.getUsername());

        ButtonType closeBtn = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeBtn);

        // Layout hiển thị thông tin
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 20; -fx-font-size: 14px;");

        // Hàng 1: Rank & Elo
        grid.add(new Label("Xếp hạng:"), 0, 0);
        Label lbRank = new Label("#" + p.getRank() + " (Elo: " + p.getElo() + ")");
        lbRank.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
        grid.add(lbRank, 1, 0);

        // Hàng 2: Thống kê
        grid.add(new Label("Thắng/Thua/Hòa:"), 0, 1);
        String stats = String.format("%d / %d / %d", p.getWins(), p.getLosses(), p.getDraws());
        grid.add(new Label(stats), 1, 1);

        // Hàng 3: Tỉ lệ thắng
        grid.add(new Label("Tỉ lệ thắng:"), 0, 2);
        Label lbRate = new Label(String.format("%.1f%%", p.getWinRate()));
        if (p.getWinRate() >= 50) lbRate.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        grid.add(lbRate, 1, 2);

        // Hàng 4: Nút Kết bạn
        Button btnAddFriend = new Button();
        btnAddFriend.setMaxWidth(Double.MAX_VALUE);
        GridPane.setColumnSpan(btnAddFriend, 2); 

        // Cập nhật trạng thái nút
        updateFriendButtonState(btnAddFriend, p.getFriendStatus(), p.getUsername());

        grid.add(btnAddFriend, 0, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.show();
    }

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
                btn.setText("Người này muốn kết bạn với bạn!");
                btn.setDisable(true);
                break;
            case NOT_FRIEND:
            default:
                btn.setText("Gửi lời mời kết bạn");
                btn.setDisable(false);
                
                btn.setOnAction(e -> {
                    btn.setDisable(true);
                    btn.setText("Đang gửi...");
                    runRemote("Gửi kết bạn", () -> {
                        try {
                            // [FIX LỖI 2] Thêm try-catch để bắt RemoteException
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
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            fx(() -> {
                                btn.setText("Gửi lời mời kết bạn");
                                btn.setDisable(false);
                                showInfo("Lỗi", "Gửi thất bại: " + ex.getMessage());
                            });
                        }
                    });
                });
                break;
        }
    }

    // ========================================================
    // UTILS
    // ========================================================

    private void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private void runRemote(String action, Runnable job) {
        if (ctx != null && ctx.io() != null) {
            ctx.io().execute(() -> {
                // Chỉ bắt lỗi runtime chung chung ở đây, còn checked exception (RemoteException)
                // phải được bắt bên trong `job` (Runnable)
                try {
                    job.run();
                } catch (Exception e) {
                    fx(() -> showInfo("Lỗi", action + ": " + e.getMessage()));
                }
            });
        }
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(title); a.setContentText(msg);
        a.show();
    }
}
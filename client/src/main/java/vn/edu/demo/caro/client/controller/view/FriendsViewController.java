package vn.edu.demo.caro.client.controller.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import vn.edu.demo.caro.client.core.AppContext;
import vn.edu.demo.caro.client.core.ClientCallbackImpl;
import vn.edu.demo.caro.client.core.WithContext;
import vn.edu.demo.caro.common.model.FriendInfo;
import vn.edu.demo.caro.common.model.FriendRequest;
import vn.edu.demo.caro.common.model.Enums.UserStatus;

import java.time.Instant;

public class FriendsViewController implements WithContext {

    private AppContext ctx;

    @FXML private ListView<FriendInfo> lvFriends; // [QUAN TRỌNG] Sửa trong FXML từ String -> FriendInfo
    @FXML private TextField tfUser;
    
    // Nếu bạn có ListView online user riêng, hãy giữ nguyên, nhưng ở đây tôi tập trung vào lvFriends
    @FXML private ListView<String> lvOnline; 

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        Object cb = ctx.stage.getProperties().get("callback");
        if (cb instanceof ClientCallbackImpl) {
            ((ClientCallbackImpl) cb).bindFriends(this);
        }

        // Cấu hình Cell Factory để vẽ từng dòng bạn bè (kèm trạng thái & nút thách đấu)
        if (lvFriends != null) {
            lvFriends.setCellFactory(param -> new FriendCell());
        }

        refresh();
    }

    @FXML
    private void onSendRequest() {
        String to = tfUser.getText().trim();
        if (to.isBlank()) return;
        
        // Gửi lời mời kết bạn (dùng thread riêng)
        runRemote("Gửi lời mời", () -> {
            ctx.lobby.sendFriendRequest(new FriendRequest(ctx.username, to, Instant.now()));
            fx(() -> {
                tfUser.clear();
                showInfo("Thành công", "Đã gửi lời mời tới " + to);
            });
        });
    }

    public void refresh() {
        fx(() -> {
            // Cập nhật danh sách bạn bè (ctx.friends giờ chứa FriendInfo)
            if (lvFriends != null) {
                lvFriends.setItems(FXCollections.observableArrayList(ctx.friends));
            }
            // Cập nhật danh sách online chung (nếu có)
            if (lvOnline != null) {
                lvOnline.getItems().setAll(ctx.onlineUsers);
            }
        });
    }

    // Xử lý khi có ai đó gửi lời mời kết bạn tới mình
    public void handleFriendRequest(FriendRequest req) {
        fx(() -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Lời mời kết bạn");
            a.setHeaderText(req.getFrom() + " muốn kết bạn với bạn");
            a.setContentText("Chấp nhận?");
            
            ButtonType btnYes = new ButtonType("Đồng ý", ButtonBar.ButtonData.YES);
            ButtonType btnNo = new ButtonType("Từ chối", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(btnYes, btnNo);

            a.showAndWait().ifPresent(type -> {
                boolean accept = (type == btnYes);
                runRemote("Phản hồi kết bạn", () -> 
                    ctx.lobby.respondFriendRequest(req.getFrom(), req.getTo(), accept)
                );
            });
        });
    }

    // --- CUSTOM CELL: HIỂN THỊ BẠN BÈ VỚI TRẠNG THÁI ---
    private class FriendCell extends ListCell<FriendInfo> {
        @Override
        protected void updateItem(FriendInfo item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null); setGraphic(null); return;
            }

            // 1. Chấm tròn trạng thái
            Circle dot = new Circle(5);
            String statusStr = "";
            
            if (item.getStatus() == UserStatus.ONLINE) {
                dot.setFill(Color.LIGHTGREEN);
                statusStr = "Online";
            } else if (item.getStatus() == UserStatus.PLAYING) {
                dot.setFill(Color.RED);
                statusStr = "Đang chơi";
            } else {
                dot.setFill(Color.GRAY);
                statusStr = "Offline";
            }

            // 2. Thông tin tên & trạng thái
            Label lbName = new Label(item.getUsername());
            lbName.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");
            
            Label lbStatus = new Label(statusStr);
            lbStatus.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11px;");

            VBox infoBox = new VBox(2, lbName, lbStatus);
            infoBox.setAlignment(Pos.CENTER_LEFT);
            
            // 3. Nút Thách đấu (Chỉ hiện khi Online rảnh)
            Button btnChallenge = new Button("Thách đấu");
            btnChallenge.setStyle("-fx-background-color: #eab308; -fx-text-fill: black; -fx-font-size: 11px; -fx-cursor: hand;");
            
            if (item.getStatus() == UserStatus.ONLINE) {
                btnChallenge.setVisible(true);
                btnChallenge.setOnAction(e -> {
                    btnChallenge.setDisable(true);
                    btnChallenge.setText("Đã gửi...");
                    // Gọi Server thách đấu
                    runRemote("Thách đấu", () -> {
                        ctx.lobby.sendChallenge(ctx.username, item.getUsername());
                    });
                });
            } else {
                btnChallenge.setVisible(false);
            }

            // Layout tổng thể
            HBox root = new HBox(10);
            root.setAlignment(Pos.CENTER_LEFT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS); // Đẩy nút sang phải cùng
            
            root.getChildren().addAll(dot, infoBox, spacer, btnChallenge);
            setGraphic(root);
        }
    }

    // --- Helpers ---
    private void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    @FunctionalInterface
    private interface RemoteJob { void run() throws Exception; }

    private void runRemote(String action, RemoteJob job) {
        if (ctx != null && ctx.io() != null) {
            ctx.io().execute(() -> {
                try {
                    job.run();
                } catch (Exception e) {
                    fx(() -> {
                        Alert a = new Alert(Alert.AlertType.ERROR);
                        a.setHeaderText(action + " thất bại");
                        a.setContentText(e.getMessage());
                        a.show();
                    });
                }
            });
        }
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(title); a.setContentText(message); a.show();
    }
}
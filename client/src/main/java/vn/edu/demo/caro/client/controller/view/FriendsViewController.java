package vn.edu.demo.caro.client.controller.view;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import vn.edu.demo.caro.client.core.*;
import vn.edu.demo.caro.common.model.FriendRequest;

import java.time.Instant;

public class FriendsViewController implements WithContext {

    private AppContext ctx;

    @FXML private ListView<String> lvFriends;
    @FXML private TextField tfUser;
    @FXML private ListView<String> lvOnline;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        Object cb = ctx.stage.getProperties().get("callback");
        if (cb instanceof ClientCallbackImpl) ((ClientCallbackImpl) cb).bindFriends(this);

        refresh();
    }

    @FXML private void onSendRequest() {
        String to = tfUser.getText().trim();
        if (to.isBlank()) return;
        try {
            ctx.lobby.sendFriendRequest(new FriendRequest(ctx.username, to, Instant.now()));
            tfUser.clear();
            showInfo("OK", "Đã gửi lời mời tới " + to);
        } catch (Exception e) {
            showInfo("Lỗi", e.getMessage());
        }
    }

    public void refresh() {
        if (lvFriends != null) lvFriends.getItems().setAll(ctx.friends);
        if (lvOnline != null) lvOnline.getItems().setAll(ctx.onlineUsers);
    }

    public void handleFriendRequest(FriendRequest req) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Lời mời kết bạn");
        a.setHeaderText(req.getFrom() + " muốn kết bạn với bạn");
        a.setContentText("Chấp nhận?");
        boolean accept = a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
        try { ctx.lobby.respondFriendRequest(req.getFrom(), req.getTo(), accept); }
        catch (Exception e) { showInfo("Lỗi", e.getMessage()); }
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(title); a.setContentText(message); a.showAndWait();
    }
}

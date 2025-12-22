package vn.edu.demo.caro.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import vn.edu.demo.caro.client.core.*;

import java.util.HashMap;
import java.util.Map;

public class MainController implements WithContext {

    private AppContext ctx;
    private ClientCallbackImpl callback;

    @FXML private Label lbUser;
    @FXML private Label lbStatus;
    @FXML private StackPane contentPane;

    @FXML private Button btnRooms;
    @FXML private Button btnChat;
    @FXML private Button btnFriends;
    @FXML private Button btnLeaderboard;
    @FXML private Button btnAi;
    @FXML private Button btnLogout;


    private final Map<String, Node> cachedViews = new HashMap<>();

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;
        Object cb = ctx.stage.getProperties().get("callback");
        if (cb instanceof ClientCallbackImpl) {
            this.callback = (ClientCallbackImpl) cb;
            this.callback.bindMain(this);
        }

        lbUser.setText(ctx.me != null ? ctx.me.toString() : ctx.username);
        pushStatus("Sẵn sàng.");
        setActive(btnRooms);
        btnRooms.setOnAction(e -> onNavRooms());
        btnChat.setOnAction(e -> onNavChat());
        btnFriends.setOnAction(e -> onNavFriends());
        btnLeaderboard.setOnAction(e -> onNavLeaderboard());
        btnAi.setOnAction(e -> onNavAi());
        btnLogout.setOnAction(e -> onLogout());

        showView("rooms", "/vn/edu/demo/caro/client/fxml/views/rooms.fxml");
    }

    @FXML private void onNavRooms() { setActive(btnRooms); showView("rooms", "/vn/edu/demo/caro/client/fxml/views/rooms.fxml"); pushStatus("Phòng & Ghép trận."); }
    @FXML private void onNavChat() { setActive(btnChat); showView("chat", "/vn/edu/demo/caro/client/fxml/views/chat.fxml"); pushStatus("Chat toàn cục."); }
    @FXML private void onNavFriends() { setActive(btnFriends); showView("friends", "/vn/edu/demo/caro/client/fxml/views/friends.fxml"); pushStatus("Bạn bè."); }
    @FXML private void onNavLeaderboard() { setActive(btnLeaderboard); showView("leaderboard", "/vn/edu/demo/caro/client/fxml/views/leaderboard.fxml"); pushStatus("Bảng xếp hạng."); }
    @FXML private void onNavAi() { setActive(btnAi); showView("ai", "/vn/edu/demo/caro/client/fxml/views/ai.fxml"); pushStatus("Chơi với máy (offline)."); }

    @FXML private void onLogout() {
        try { if (ctx.lobby != null && ctx.username != null) ctx.lobby.logout(ctx.username); } catch (Exception ignored) {}
        ctx.username = null; ctx.me = null; ctx.currentRoomId = null;
        ctx.sceneManager.showLogin();
    }

    public void pushStatus(String text) { lbStatus.setText(text); }

    private void showView(String key, String fxml) {
        try {
            Node view = cachedViews.get(key);
            if (view == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
                view = loader.load();
                Object c = loader.getController();
                if (c instanceof WithContext) ((WithContext) c).init(ctx);
                cachedViews.put(key, view);
            }
            contentPane.getChildren().setAll(view);
        } catch (Exception e) {
    e.printStackTrace(); // in đầy đủ ra terminal

    Throwable root = e;
    while (root.getCause() != null) root = root.getCause();

    Alert a = new Alert(Alert.AlertType.ERROR);
    a.setTitle("UI error");
    a.setHeaderText("Cannot load view");
    a.setContentText(root.toString()); // hiện đúng nguyên nhân gốc
    a.showAndWait();
}
    }

    private void setActive(Button active) {
        Button[] all = {btnRooms, btnChat, btnFriends, btnLeaderboard, btnAi};
        for (Button b : all) {
            if (!b.getStyleClass().contains("nav-btn")) b.getStyleClass().add("nav-btn");
            b.getStyleClass().remove("active");
        }
        if (!active.getStyleClass().contains("active")) active.getStyleClass().add("active");
    }
}

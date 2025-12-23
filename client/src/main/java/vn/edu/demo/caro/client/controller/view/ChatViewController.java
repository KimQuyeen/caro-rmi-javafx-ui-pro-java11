package vn.edu.demo.caro.client.controller.view;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import vn.edu.demo.caro.client.controller.view.cell.GlobalChatCell;
import vn.edu.demo.caro.client.core.AppContext;
import vn.edu.demo.caro.client.core.WithContext;
import vn.edu.demo.caro.common.model.ChatMessage;

import java.time.Instant;

public class ChatViewController implements WithContext {

    private AppContext ctx;

    @FXML private ListView<ChatMessage> lvGlobalChat;
    @FXML private TextField tf;

    @FXML
    private void initialize() {
        if (lvGlobalChat != null) lvGlobalChat.setPlaceholder(new Label("Chưa có tin nhắn"));
    }

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        lvGlobalChat.setItems(ctx.getGlobalChatStore().messages());
        lvGlobalChat.setCellFactory(v -> new GlobalChatCell(ctx.username));

        ctx.getGlobalChatStore().messages().addListener((ListChangeListener<ChatMessage>) c -> {
            boolean added = false;
            while (c.next()) if (c.wasAdded()) added = true;
            if (added) Platform.runLater(() -> lvGlobalChat.scrollTo(lvGlobalChat.getItems().size() - 1));
        });
    }

    @FXML
    private void onSend() {
        if (ctx == null || ctx.lobby == null) return;

        String text = tf.getText() == null ? "" : tf.getText().trim();
        if (text.isBlank()) return;

        ChatMessage msg = new ChatMessage(ctx.username, "GLOBAL", text, Instant.now());

        tf.clear();
        tf.setDisable(true);

        ctx.io().execute(() -> {
            try {
                ctx.lobby.sendGlobalChat(msg);
            } catch (Exception e) {
                Platform.runLater(() -> showInfo("Lỗi", e.getMessage()));
            } finally {
                Platform.runLater(() -> tf.setDisable(false));
            }
        });
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.show(); // non-blocking
    }
}

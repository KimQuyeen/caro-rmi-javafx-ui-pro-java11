package vn.edu.demo.caro.client.controller.view;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import vn.edu.demo.caro.client.core.*;
import vn.edu.demo.caro.common.model.ChatMessage;

import java.time.Instant;

public class ChatViewController implements WithContext {

    private AppContext ctx;

    @FXML private TextArea ta;
    @FXML private TextField tf;

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        Object cb = ctx.stage.getProperties().get("callback");
        if (cb instanceof ClientCallbackImpl) ((ClientCallbackImpl) cb).bindChat(this);

        ta.setEditable(false);
        ta.appendText("Chat toàn cục.\n");
    }

    @FXML private void onSend() {
        String text = tf.getText().trim();
        if (text.isBlank()) return;
        try {
            ctx.lobby.sendGlobalChat(new ChatMessage(ctx.username, "GLOBAL", text, Instant.now()));
            tf.clear();
        } catch (Exception e) {
            showInfo("Lỗi", e.getMessage());
        }
    }

    public void append(ChatMessage msg) { if (ta != null) ta.appendText(msg.toString() + "\n"); }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(title); a.setContentText(message); a.showAndWait();
    }
}

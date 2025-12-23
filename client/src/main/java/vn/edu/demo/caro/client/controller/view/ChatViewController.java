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
        if (lvGlobalChat != null) {
            lvGlobalChat.setPlaceholder(new Label("Chưa có tin nhắn"));
        }
    }

    @Override
    public void init(AppContext ctx) {
        this.ctx = ctx;

        // Bind dữ liệu vào ListView
        lvGlobalChat.setItems(ctx.getGlobalChatStore().messages());
        
        // Sử dụng Cell Factory để hiện tên/màu sắc đẹp hơn
        lvGlobalChat.setCellFactory(v -> new GlobalChatCell(ctx.username));

        // Tự động cuộn xuống khi có tin nhắn mới
        ctx.getGlobalChatStore().messages().addListener((ListChangeListener<ChatMessage>) c -> {
            boolean added = false;
            while (c.next()) {
                if (c.wasAdded()) added = true;
            }
            if (added) {
                Platform.runLater(() -> lvGlobalChat.scrollTo(lvGlobalChat.getItems().size() - 1));
            }
        });
    }

    @FXML
    private void onSend() {
        if (ctx == null || ctx.lobby == null) return;

        String text = (tf.getText() == null) ? "" : tf.getText().trim();
        if (text.isBlank()) return;

        // 1. Tạo object tin nhắn
        ChatMessage msg = new ChatMessage(ctx.username, "GLOBAL", text, Instant.now());

        // 2. [QUAN TRỌNG] Xóa ô nhập ngay lập tức để trải nghiệm mượt mà
        tf.clear();
        
        // [QUAN TRỌNG] KHÔNG được disable TextField (xóa dòng tf.setDisable(true))
        // Việc disable sẽ làm người dùng cảm thấy app bị đơ.

        // 3. Gửi tin nhắn trong luồng nền (Background Thread)
        if (ctx.io() != null) {
            ctx.io().execute(() -> {
                try {
                    ctx.lobby.sendGlobalChat(msg);
                } catch (Exception e) {
                    // Nếu lỗi mạng thì mới hiện thông báo và trả lại chữ vào ô nhập
                    Platform.runLater(() -> {
                        tf.setText(text); // Khôi phục tin nhắn để gửi lại
                        showInfo("Lỗi gửi tin", e.getMessage());
                    });
                }
            });
        }
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.show(); // Dùng show() thay vì showAndWait() để không chặn các thao tác khác
    }
}
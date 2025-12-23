package vn.edu.demo.caro.client.state;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import vn.edu.demo.caro.common.model.ChatMessage;

public final class GlobalChatStore {
    private static final GlobalChatStore INSTANCE = new GlobalChatStore();

    private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();

    private GlobalChatStore() {}

    public static GlobalChatStore get() {
        return INSTANCE;
    }

    public ObservableList<ChatMessage> messages() {
        return messages;
    }

    public void add(ChatMessage msg) {
        if (msg == null) return;

        // luôn chạy trên FX thread để UI cập nhật ngay
        if (Platform.isFxApplicationThread()) {
            messages.add(msg);
        } else {
            Platform.runLater(() -> messages.add(msg));
        }
    }

    public void clear() {
        if (Platform.isFxApplicationThread()) {
            messages.clear();
        } else {
            Platform.runLater(messages::clear);
        }
    }
}

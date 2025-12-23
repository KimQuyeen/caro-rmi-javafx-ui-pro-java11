package vn.edu.demo.caro.client.controller.view.cell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.demo.caro.common.model.ChatMessage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class GlobalChatCell extends ListCell<ChatMessage> {

    private final String me;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public GlobalChatCell(String me) {
        this.me = me;
    }

    @Override
    protected void updateItem(ChatMessage msg, boolean empty) {
        super.updateItem(msg, empty);

        if (empty || msg == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        boolean isMe = msg.getFrom() != null && msg.getFrom().equals(me);

        Label lbFrom = new Label(msg.getFrom());
        lbFrom.getStyleClass().add("chat-from");

        Label lbTime = new Label(msg.getAt() == null ? "" : TIME_FMT.format(msg.getAt()));
        lbTime.getStyleClass().add("chat-time");

        HBox header = new HBox(8, lbFrom, lbTime);
        header.setAlignment(Pos.CENTER_LEFT);

        Label lbContent = new Label(msg.getContent());
        lbContent.setWrapText(true);
        lbContent.getStyleClass().add("chat-content");

        VBox bubble = new VBox(4, header, lbContent);
        bubble.getStyleClass().add(isMe ? "chat-bubble-me" : "chat-bubble-other");
        bubble.setPadding(new Insets(8, 10, 8, 10));
        bubble.setMaxWidth(420);

        HBox row = new HBox(bubble);
        row.setPadding(new Insets(6, 10, 6, 10));
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        setText(null);
        setGraphic(row);
    }
}

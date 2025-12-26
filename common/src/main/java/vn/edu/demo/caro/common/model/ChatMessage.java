package vn.edu.demo.caro.common.model;

import java.io.Serializable;
import java.time.Instant;

public class ChatMessage implements Serializable {
    private final String from;  // Đây là biến chứa tên người gửi
    private final String scope; // GLOBAL or ROOM:<id>
    private final String content;
    private final Instant at;

    public ChatMessage(String from, String scope, String content, Instant at) {
        this.from = from;
        this.scope = scope;
        this.content = content;
        this.at = at;
    }

    // --- Getter chuẩn ---

    public String getFrom() {
        return from;
    }

    // [QUAN TRỌNG] Hàm này giúp Client gọi msg.getSender() mà không bị lỗi
    // Nó trả về giá trị của biến 'from'
    public String getSender() {
        return from;
    }

    public String getScope() {
        return scope;
    }

    public String getContent() {
        return content;
    }

    public Instant getAt() {
        return at;
    }

    @Override
    public String toString() {
        return "[" + at + "] " + from + ": " + content;
    }
}
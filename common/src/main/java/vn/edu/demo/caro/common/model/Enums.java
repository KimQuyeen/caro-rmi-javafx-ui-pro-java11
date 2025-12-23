package vn.edu.demo.caro.common.model;

import java.io.Serializable;

public final class Enums {
    private Enums() {}

    public enum RoomStatus implements Serializable { WAITING, PLAYING, CLOSED }
    public enum Mark implements Serializable { EMPTY, X, O }
    public enum GameEndReason implements Serializable { WIN, DRAW, RESIGN, ABORT, TIMEOUT }
public enum UserStatus {
    OFFLINE, // Không online (Màu xám)
    ONLINE,  // Online rảnh rỗi (Màu xanh - Có thể thách đấu)
    PLAYING  // Đang trong trận (Màu đỏ - Không thể thách đấu)
}
    public enum PostGameChoice {
        REMATCH, RETURN
    }
}

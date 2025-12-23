package vn.edu.demo.caro.common.model;

import java.io.Serializable;

public final class Enums {
    private Enums() {}

    public enum RoomStatus implements Serializable { WAITING, PLAYING, CLOSED }
    public enum Mark implements Serializable { EMPTY, X, O }
    public enum GameEndReason implements Serializable { WIN, DRAW, RESIGN, ABORT, TIMEOUT }

    public enum PostGameChoice {
        REMATCH, RETURN
    }
}

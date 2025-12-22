package vn.edu.demo.caro.common.model;

import vn.edu.demo.caro.common.model.Enums.RoomStatus;

import java.io.Serializable;
import java.time.Instant;

public class RoomInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String name;
    private String owner;

    private int maxPlayers;
    private int currentPlayers;

    private RoomStatus status;
    private Instant createdAt;

    // ---- new options ----
    private int boardSize;
    private boolean blockTwoEnds;

    private boolean passwordEnabled; // không gửi password ra client
    private boolean timed;
    private int timeLimitSeconds;
    // alias để tương thích code cũ
public String getRoomId() { return id; }
public void setRoomId(String id) { this.id = id; }


    public RoomInfo() {}

    public RoomInfo(String id, String name, String owner,
                    int maxPlayers, int currentPlayers, RoomStatus status, Instant createdAt,
                    int boardSize, boolean blockTwoEnds,
                    boolean passwordEnabled, boolean timed, int timeLimitSeconds) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.maxPlayers = maxPlayers;
        this.currentPlayers = currentPlayers;
        this.status = status;
        this.createdAt = createdAt;

        this.boardSize = boardSize;
        this.blockTwoEnds = blockTwoEnds;
        this.passwordEnabled = passwordEnabled;
        this.timed = timed;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public RoomInfo(String id, String name, String owner,
                int maxPlayers, int currentPlayers, RoomStatus status, Instant createdAt) {
    this(id, name, owner, maxPlayers, currentPlayers, status, createdAt,
         15, false, false, false, 0); // default tuỳ bạn
}

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public int getCurrentPlayers() { return currentPlayers; }
    public void setCurrentPlayers(int currentPlayers) { this.currentPlayers = currentPlayers; }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public int getBoardSize() { return boardSize; }
    public void setBoardSize(int boardSize) { this.boardSize = boardSize; }

    public boolean isBlockTwoEnds() { return blockTwoEnds; }
    public void setBlockTwoEnds(boolean blockTwoEnds) { this.blockTwoEnds = blockTwoEnds; }

    public boolean isPasswordEnabled() { return passwordEnabled; }
    public void setPasswordEnabled(boolean passwordEnabled) { this.passwordEnabled = passwordEnabled; }

    public boolean isTimed() { return timed; }
    public void setTimed(boolean timed) { this.timed = timed; }

    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }

    @Override
public String toString() {
    String rules = "Bàn " + boardSize + " | " +
            (blockTwoEnds ? "Chặn 2 đầu" : "Không chặn") + " | " +
            (timed ? (timeLimitSeconds + "s/lượt") : "Không time") + " | " +
            (passwordEnabled ? "Có mật khẩu" : "Không mật khẩu");

    return String.format("%s (%d/%d) - Chủ: %s - %s",
            name, currentPlayers, maxPlayers, owner, rules);
}

}

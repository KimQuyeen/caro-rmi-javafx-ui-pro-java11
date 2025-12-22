package vn.edu.demo.caro.common.model;

import java.io.Serializable;

public class RoomCreateRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String roomName;

    private int boardSize;
    private boolean blockTwoEnds;

    private boolean passwordEnabled;
    private String password;

    private boolean timed;
    private int timeLimitSeconds;

    public RoomCreateRequest() {}

    // getters/setters
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public int getBoardSize() { return boardSize; }
    public void setBoardSize(int boardSize) { this.boardSize = boardSize; }

    public boolean isBlockTwoEnds() { return blockTwoEnds; }
    public void setBlockTwoEnds(boolean blockTwoEnds) { this.blockTwoEnds = blockTwoEnds; }

    public boolean isPasswordEnabled() { return passwordEnabled; }
    public void setPasswordEnabled(boolean passwordEnabled) { this.passwordEnabled = passwordEnabled; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isTimed() { return timed; }
    public void setTimed(boolean timed) { this.timed = timed; }

    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }
}

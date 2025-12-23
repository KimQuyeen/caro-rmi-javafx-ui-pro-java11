package vn.edu.demo.caro.common.model;

import java.io.Serializable;
import vn.edu.demo.caro.common.model.Enums.UserStatus; // Đảm bảo import đúng Enum

public class FriendInfo implements Serializable {
    private String username;
    private UserStatus status;

    public FriendInfo(String username, UserStatus status) {
        this.username = username;
        this.status = status;
    }

    public String getUsername() { return username; }
    public UserStatus getStatus() { return status; }
}
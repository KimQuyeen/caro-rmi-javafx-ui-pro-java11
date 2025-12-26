package vn.edu.demo.caro.server.dao;

import vn.edu.demo.caro.server.db.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FriendDao {

    private final Db db;

    // Constructor nhận Db (để ServerController gọi)
    public FriendDao(Db db) {
        this.db = db;
    }

    /**
     * Lấy danh sách bạn bè
     * Sửa: Dùng cột 'username' và 'friend'
     */
    public List<String> listFriends(String user) throws SQLException {
        List<String> list = new ArrayList<>();
        // Query: Tìm trong cột username, lấy ra cột friend
        String sql = "SELECT friend FROM friends WHERE username = ?";
        
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("friend"));
                }
            }
        }
        return list;
    }

    /**
     * Kiểm tra 2 người có phải bạn không
     * Sửa: Dùng cột 'username' và 'friend'
     */
    public boolean areFriends(String u1, String u2) throws SQLException {
        String sql = "SELECT 1 FROM friends WHERE username = ? AND friend = ? LIMIT 1";
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u1);
            ps.setString(2, u2);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Tạo yêu cầu kết bạn
     * (Bảng friend_requests vẫn dùng from_user, to_user như cũ là đúng)
     */
    public void createFriendRequest(String from, String to) throws SQLException {
        if (hasPendingRequest(from, to)) return;

        String sql = "INSERT INTO friend_requests(from_user, to_user, status, created_at) VALUES(?, ?, 'PENDING', NOW())";
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.executeUpdate();
        }
    }
    
    // Alias hỗ trợ code cũ
    public void addFriendRequest(String from, String to) throws SQLException {
        createFriendRequest(from, to);
    }

    public boolean hasPendingRequest(String from, String to) throws SQLException {
        String sql = "SELECT 1 FROM friend_requests WHERE from_user = ? AND to_user = ? AND status = 'PENDING' LIMIT 1";
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<String> getIncomingRequests(String username) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT from_user FROM friend_requests WHERE to_user = ? AND status = 'PENDING'";
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("from_user"));
                }
            }
        }
        return list;
    }

    public void resolveLatestPending(String fromUser, String toUser, boolean accept) throws SQLException {
        String status = accept ? "ACCEPTED" : "REJECTED";
        String updateSql = "UPDATE friend_requests SET status = ? WHERE from_user = ? AND to_user = ? AND status = 'PENDING'";
        
        try (Connection c = db.connect()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false); // Transaction
            
            try {
                try (PreparedStatement ps = c.prepareStatement(updateSql)) {
                    ps.setString(1, status);
                    ps.setString(2, fromUser);
                    ps.setString(3, toUser);
                    ps.executeUpdate();
                }

                if (accept) {
                    addFriendPairInternal(c, fromUser, toUser);
                }
                
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(oldAutoCommit);
            }
        }
    }

    public void addFriendPair(String u1, String u2) throws SQLException {
        try (Connection c = db.connect()) {
            addFriendPairInternal(c, u1, u2);
        }
    }

    // Hàm nội bộ: Thêm vào bảng friends
    // Sửa: Insert vào cột (username, friend)
    private void addFriendPairInternal(Connection c, String u1, String u2) throws SQLException {
        if (areFriendsInternal(c, u1, u2)) return;

        // Insert 2 chiều để khi A query ra B, và B query cũng ra A
        String sql = "INSERT INTO friends(username, friend, created_at) VALUES (?, ?, NOW()), (?, ?, NOW())";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            // Chiều A -> B
            ps.setString(1, u1);
            ps.setString(2, u2);
            
            // Chiều B -> A
            ps.setString(3, u2);
            ps.setString(4, u1);
            
            ps.executeUpdate();
        }
    }

    private boolean areFriendsInternal(Connection c, String u1, String u2) throws SQLException {
        String sql = "SELECT 1 FROM friends WHERE username = ? AND friend = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u1);
            ps.setString(2, u2);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
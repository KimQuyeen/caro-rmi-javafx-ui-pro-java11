package vn.edu.demo.caro.server.dao;

import vn.edu.demo.caro.server.db.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FriendDao {

    public List<String> listFriends(String username) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT friend FROM friends WHERE username=? ORDER BY friend")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getString("friend"));
                return out;
            }
        }
    }

    public void addFriendPair(String a, String b) throws SQLException {
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT IGNORE INTO friends(username,friend) VALUES(?,?)")) {
                ps.setString(1, a);
                ps.setString(2, b);
                ps.executeUpdate();

                ps.setString(1, b);
                ps.setString(2, a);
                ps.executeUpdate();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
            c.commit();
        }
    }

    // Giữ lại để tương thích code cũ: sendFriendRequest(req) đang gọi addFriendRequest
    public void addFriendRequest(String from, String to) throws SQLException {
        createFriendRequest(from, to);
    }

    public void createFriendRequest(String from, String to) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO friend_requests(from_user,to_user,status) VALUES(?,?,'PENDING')")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.executeUpdate();
        }
    }

    public void resolveLatestPending(String from, String to, boolean accept) throws SQLException {
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);

            try {
                long id = -1;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM friend_requests " +
                        "WHERE from_user=? AND to_user=? AND status='PENDING' " +
                        "ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
                    ps.setString(1, from);
                    ps.setString(2, to);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) id = rs.getLong("id");
                    }
                }

                if (id != -1) {
                    try (PreparedStatement ps2 = c.prepareStatement(
                            "UPDATE friend_requests SET status=? WHERE id=?")) {
                        ps2.setString(1, accept ? "ACCEPTED" : "DENIED");
                        ps2.setLong(2, id);
                        ps2.executeUpdate();
                    }
                }

            } catch (SQLException e) {
                c.rollback();
                throw e;
            }

            c.commit();
        }
    }

    public boolean areFriends(String a, String b) throws SQLException {
        String sql = "SELECT 1 FROM friends WHERE username=? AND friend=? LIMIT 1";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean hasPendingRequest(String from, String to) throws SQLException {
        String sql = "SELECT 1 FROM friend_requests WHERE from_user=? AND to_user=? AND status='PENDING' LIMIT 1";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}

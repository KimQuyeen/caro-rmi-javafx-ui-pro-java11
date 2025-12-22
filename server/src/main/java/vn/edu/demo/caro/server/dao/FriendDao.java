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
                ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
                ps.setString(1, b); ps.setString(2, a); ps.executeUpdate();
            }
            c.commit();
        }
    }

    public void addFriendRequest(String from, String to) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO friend_requests(from_user,to_user,status) VALUES(?,?, 'PENDING')")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.executeUpdate();
        }
    }

    public void resolveLatestPending(String from, String to, boolean accept) throws SQLException {
        // Mark the latest pending request as accepted/denied
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);

            long id = -1;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM friend_requests WHERE from_user=? AND to_user=? AND status='PENDING' ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
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
            c.commit();
        }
    }
}

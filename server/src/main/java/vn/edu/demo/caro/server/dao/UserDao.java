package vn.edu.demo.caro.server.dao;

import vn.edu.demo.caro.common.model.UserProfile;
import vn.edu.demo.caro.server.db.Db;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDao {

    public void ensureUser(String username, String password) throws SQLException {
        try (Connection c = Db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT IGNORE INTO users(username,password) VALUES(?,?)")) {
                ps.setString(1, username);
                ps.setString(2, password);
                ps.executeUpdate();
            }
        }
    }

    public Optional<UserRecord> find(String username) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT username,password,wins,losses,draws,elo,banned_until,ban_reason FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp bannedUntil = rs.getTimestamp("banned_until");
                return Optional.of(new UserRecord(
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getInt("wins"),
                        rs.getInt("losses"),
                        rs.getInt("draws"),
                        rs.getInt("elo"),
                        bannedUntil == null ? null : bannedUntil.toInstant(),
                        rs.getString("ban_reason")
                ));
            }
        }
    }

    public boolean exists(String username) throws SQLException {
    String sql = "SELECT 1 FROM users WHERE username=? LIMIT 1";
    try (var c = Db.getConnection();
         var ps = c.prepareStatement(sql)) {
        ps.setString(1, username);
        try (var rs = ps.executeQuery()) {
            return rs.next();
        }
    }
}

public void create(String username, String password) throws SQLException {
    String sql = "INSERT INTO users(username,password,wins,losses,draws,elo) VALUES(?,?,?,?,?,?)";
    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, username);
        ps.setString(2, password);
        ps.setInt(3, 0);
        ps.setInt(4, 0);
        ps.setInt(5, 0);
        ps.setInt(6, 1000); // hoặc 0/100 tùy bạn muốn default ELO
        ps.executeUpdate();
    }
}


    public void updateStats(String username, int wins, int losses, int draws, int elo) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET wins=?, losses=?, draws=?, elo=? WHERE username=?")) {
            ps.setInt(1, wins);
            ps.setInt(2, losses);
            ps.setInt(3, draws);
            ps.setInt(4, elo);
            ps.setString(5, username);
            ps.executeUpdate();
        }
    }

    public void setBan(String username, Instant bannedUntil, String reason) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET banned_until=?, ban_reason=? WHERE username=?")) {
            if (bannedUntil == null) ps.setTimestamp(1, null);
            else ps.setTimestamp(1, Timestamp.from(bannedUntil));
            ps.setString(2, reason);
            ps.setString(3, username);
            ps.executeUpdate();
        }
    }

    public List<UserProfile> topElo(int top) throws SQLException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT username,wins,losses,draws,elo FROM users ORDER BY elo DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, top));
            try (ResultSet rs = ps.executeQuery()) {
                List<UserProfile> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new UserProfile(
                            rs.getString("username"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("draws"),
                            rs.getInt("elo")
                    ));
                }
                return out;
            }
        }
    }

    public static class UserRecord {
        public final String username;
        public final String password;
        public final int wins, losses, draws, elo;
        public final Instant bannedUntil;
        public final String banReason;

        public UserRecord(String username, String password, int wins, int losses, int draws, int elo, Instant bannedUntil, String banReason) {
            this.username = username;
            this.password = password;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.elo = elo;
            this.bannedUntil = bannedUntil;
            this.banReason = banReason;
        }
    }

    public UserRecord findByUsername(String username) throws SQLException {
    return find(username).orElse(null);
}

public int countUsersHigherElo(int elo) throws SQLException {
    String sql = "SELECT COUNT(*) AS c FROM users WHERE elo > ?";
    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, elo);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return 0;
            return rs.getInt("c");
        }
    }
}


}

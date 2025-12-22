package vn.edu.demo.caro.common.model;

import java.io.Serializable;
import java.time.Instant;

public class FriendRequest implements Serializable {
    private final String from;
    private final String to;
    private final Instant at;

    public FriendRequest(String from, String to, Instant at) {
        this.from = from;
        this.to = to;
        this.at = at;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public Instant getAt() { return at; }
}

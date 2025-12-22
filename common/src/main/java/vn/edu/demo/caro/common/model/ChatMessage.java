package vn.edu.demo.caro.common.model;

import java.io.Serializable;
import java.time.Instant;

public class ChatMessage implements Serializable {
    private final String from;
    private final String scope; // GLOBAL or ROOM:<id>
    private final String content;
    private final Instant at;



    public ChatMessage(String from, String scope, String content, Instant at) {
        this.from = from;
        this.scope = scope;
        this.content = content;
        this.at = at;
    }

    public String getFrom() { return from; }
    public String getScope() { return scope; }
    public String getContent() { return content; }
    public Instant getAt() { return at; }

    @Override public String toString() {
        return "[" + at + "] " + from + ": " + content;
    }
}

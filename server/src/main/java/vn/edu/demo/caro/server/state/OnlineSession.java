package vn.edu.demo.caro.server.state;

import vn.edu.demo.caro.common.rmi.ClientCallback;

public class OnlineSession {
    public final String username;
    public volatile ClientCallback callback;

    public OnlineSession(String username, ClientCallback callback) {
        this.username = username;
        this.callback = callback;
    }
}

package vn.edu.demo.caro.client.core;

public final class RmiConfig {
    private RmiConfig() {}
    public static String host() { return System.getProperty("rmi.host", "127.0.0.1"); }
    public static int port() { return Integer.parseInt(System.getProperty("rmi.port", "1099")); }
    public static String name() { return System.getProperty("rmi.name", "CaroLobby"); }
}

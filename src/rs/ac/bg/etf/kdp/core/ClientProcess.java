package rs.ac.bg.etf.kdp.core;

import java.rmi.*;
import java.util.UUID;
import java.rmi.server.*;
import java.rmi.registry.*;

import rs.ac.bg.etf.kdp.utils.*;

public class ClientProcess implements IClientServer {
    static {
        try {
            PropertyLoader.loadConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final String SERVER_ROUTE = System.getProperty("server.route");
    private static final int SERVER_PORT = Integer.parseInt(System.getProperty("server.port"));
    private ConnectionMonitor monitor = null;
    private final String host;
    private final int port;
    private final UUID uuid = UUID.randomUUID();
    private IServerClient server = null;

    public ClientProcess(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private boolean connectToServer() {
        try {
            UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (IServerClient) registry.lookup(SERVER_ROUTE);
            server.register(uuid, this);
        } catch (Exception e) {
            return false;
        }
        final var that = this;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                UnicastRemoteObject.unexportObject(that, true);
            } catch (NoSuchObjectException e) {
                throw new RuntimeException(e);
            }
        }));
        monitor = new ConnectionMonitor(server, 5000, uuid);
        monitor.addEventListener(new ConnectionListener() {
            @Override
            public void onPingComplete(long ping) {
                System.out.printf("Ping: %d ms\n", ping);
            }

            @Override
            public void onConnectionLost() {
                System.err.println("Connection lost!");
            }

            @Override
            public void onReconnecting() {
                System.err.println("Reconnecting...");
            }

            @Override
            public void onReconnected(long ping) {
                System.out.printf("Reconnected, ping is %d ms\n", ping);
            }

            @Override
            public void onReconnectionFailed() {
                System.err.println("Reconnection failed!");
                System.exit(0);
            }
        });
        monitor.start();
        return true;
    }

    public static void main(String[] args) {
        ClientProcess cp = new ClientProcess("localhost", SERVER_PORT);
        if (!cp.connectToServer()) {
            System.err.println("Failed to connect to server!");
            System.exit(0);
        }
    }

}

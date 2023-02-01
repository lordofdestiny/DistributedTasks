package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.*;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class WorkerProcess implements IWorkerServer {
    static {
        try {
            PropertyLoader.loadConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int SERVER_PING_INTERVAL = Integer.parseInt(System.getProperty("worker.pingInterval"));
    private static final String DATE_FORMAT_STR = "dd.MM.YYYY. HH:mm:ss";
    private static final DateFormat df = new SimpleDateFormat(DATE_FORMAT_STR);
    private static final String SERVER_ROUTE = System.getProperty("server.route");
    private final UUID uuid = UUID.randomUUID();
    private final String host;
    private final int port;
    private IServerWorker server = null;
    private ConnectionMonitor connectionTracker;

    public WorkerProcess(String host, int port) {
        this.host = host;
        this.port = port;

    }

    @Override
    public void ping() {
        if (connectionTracker != null && connectionTracker.connected()) {
            System.out.printf("[%s]: Ping!\n", now());
        }
        try {
            server.ping(uuid);
        } catch (RemoteException e) {
            System.err.printf("[%s]: Lost connection to server!", now());
            System.err.println("Reconnecting...");
        }
    }

    private boolean connectToServer() {
        try {
            UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (IServerWorker) registry.lookup(SERVER_ROUTE);
            server.register(uuid, this);
        } catch (RemoteException | NotBoundException e) {
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
        connectionTracker = new ConnectionMonitor(server, SERVER_PING_INTERVAL, uuid);
        connectionTracker.addEventListener(new ConnectionListener(){
            @Override
            public void onPingComplete(long ping) {
                System.out.printf("[%s]: Ping is %d ms\n", now(), ping);
            }
            @Override
            public void onConnectionLost() {
                System.err.println("Lost connection to server!");
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
                System.err.println("Could not reconnect to server! Exiting...");
                System.exit(0);
            }
        });
        connectionTracker.start();
        return true;
    }

    private static String now() {
        return df.format(new Date());
    }

    public static void main(String[] args) {
        WorkerProcess pw = new WorkerProcess("localhost", 8080);
        if (!pw.connectToServer()) {
            System.err.println("Failed to connect to server!");
            System.exit(0);
        }
    }
}

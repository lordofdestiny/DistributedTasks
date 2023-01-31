package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.PropertyLoader;

import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class ProcessWorker implements IRMIProcessWorker {
    static {
        try {
            PropertyLoader.loadConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int SERVER_PING_INTERVAL = 10000;
    private static final String DATE_FORMAT_STR = "dd.MM.YYYY. HH:mm:ss";
    private static final DateFormat df = new SimpleDateFormat(DATE_FORMAT_STR);
    private final UUID uuid = UUID.randomUUID();
    private final String host;
    private final int port;
    private IRMICentralServer server = null;
    private Long lastServerOnlineTimestamp;
    private boolean connected = false;

    public ProcessWorker(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void ping() {
        if (connected) {
            System.out.printf("Ping from server at %s!\n", df.format(new Date()));
        }
        try {
            server.ping(uuid);
        } catch (RemoteException e) {
            System.err.println("Lost connection to server!");
            System.out.println("Reconnecting...");
        }
    }

    private boolean connectToServer() {
        try {
            final var that = this;
            UnicastRemoteObject.exportObject(this, 0);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    UnicastRemoteObject.unexportObject(that, true);
                } catch (NoSuchObjectException e) {
                    throw new RuntimeException(e);
                }
            }));
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (IRMICentralServer) registry.lookup("/CentralServer");
            server.registerWorker(uuid, this);
            lastServerOnlineTimestamp = System.currentTimeMillis();
            final var connectionTracker = new Thread(this::connectionTracker);
            connectionTracker.start();
            return true;
        } catch (RemoteException | NotBoundException e) {
            return false;
        }
    }

    private Optional<Long> pingServer() {
        try {
            final var start = System.currentTimeMillis();
            server.ping(uuid);
            final var end = System.currentTimeMillis();
            return Optional.of(end - start);
        } catch (RemoteException e) {
            return Optional.empty();
        }
    }

    private int reconnectToServer() {
        if (System.currentTimeMillis() - lastServerOnlineTimestamp > 60 * 1000) {
            System.err.println("Could not reconnect to server! Exiting...");
            System.exit(0);
            return -1;
        }
        System.out.println("Reconnecting...");
        final var ping = pingServer();
        if (ping.isPresent()) {
            System.out.printf("Reconnected to server is %d ms\n", ping.get());
            return 0;
        }
        return 1;
    }

    private void connectionTracker() {
        final var ping = pingServer();
        if (ping.isPresent()) {
            System.out.printf("Ping to server is %d ms\n", ping.get());
            lastServerOnlineTimestamp = System.currentTimeMillis();
        } else {
            System.err.println("Lost connection to server!");
            connected = false;
            new Thread(()->{
                while(true) {
                    final int status = reconnectToServer();
                    if(status <= 0) break;
                    reconnectToServer();
                    try {
                        //noinspection BusyWait
                        Thread.sleep(SERVER_PING_INTERVAL);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }
    }

    public static void main(String[] args) {
        ProcessWorker pw = new ProcessWorker("147.91.12.72", 8080);
        final var connected = pw.connectToServer();
        if (!connected) {
            System.err.println("Failed to connect to server!");
            System.exit(0);
        }
    }
}

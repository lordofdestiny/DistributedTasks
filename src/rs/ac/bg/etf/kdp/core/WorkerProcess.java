package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.PropertyLoader;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class WorkerProcess implements IRMIWorkerProcess {
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
    private IRMIServerProcess server = null;
    private Long lastOnlineTime;
    private boolean connected = false;

    public WorkerProcess(String host, int port) {
        this.host = host;
        this.port = port;

    }

    @Override
    public void ping() {
        if (connected) {
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
            final var that = this;
            UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (IRMIServerProcess) registry.lookup(SERVER_ROUTE);
            server.registerWorker(uuid, this);
            lastOnlineTime = System.currentTimeMillis();
            final var connectionTracker = new Thread(this::connectionTracker);
            connectionTracker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    UnicastRemoteObject.unexportObject(that, true);
                } catch (NoSuchObjectException e) {
                    throw new RuntimeException(e);
                }
            }));
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

    private void reconnectToServer() {
        while (System.currentTimeMillis() - lastOnlineTime < 60 * 1000) {
            System.out.println("Reconnecting...");
            final var ping = pingServer();
            if (ping.isPresent()) {
                System.out.printf("Reconnected, ping is %d ms\n", ping.get());
                return;
            }
        }
        System.err.println("Could not reconnect to server! Exiting...");
        System.exit(0);
    }

    private void connectionTracker() {
        while (true) {
            final var ping = pingServer();
            if (ping.isPresent()) {
                System.out.printf("Ping to server is %d ms\n", ping.get());
                lastOnlineTime = System.currentTimeMillis();
                try {
                    //noinspection BusyWait
                    Thread.sleep(SERVER_PING_INTERVAL);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            System.err.println("Lost connection to server!");
            connected = false;
            reconnectToServer();
        }
    }

    private static String now() {
        return df.format(new Date());
    }

    public static void main(String[] args) {
        WorkerProcess pw = new WorkerProcess("localhost", 8080);
        final var connected = pw.connectToServer();
        if (!connected) {
            System.err.println("Failed to connect to server!");
            System.exit(0);
        }
    }
}

package rs.ac.bg.etf.kdp.core.worker;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.ConnectionMonitor;
import rs.ac.bg.etf.kdp.core.IServerWorker;
import rs.ac.bg.etf.kdp.core.IServerWorker.AlreadyRegisteredException;
import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.*;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider.ServerUnavailableException;

public class WorkerProcess implements IWorkerServer, Unreferenced {
    static {
        Configuration.load();
    }

    private static final String DATE_FORMAT_STR = "dd.MM.YYYY. HH:mm:ss";
    private static final DateFormat df = new SimpleDateFormat(DATE_FORMAT_STR);
    private final UUID uuid = UUID.randomUUID();
    private final String host;
    private final int port;
    private IServerWorker server = null;
    private ConnectionMonitor monitor;

    public WorkerProcess(String host, int port) {
        this.host = host;
        this.port = port;

    }

    @Override
    public void ping() {
        if (monitor != null && monitor.connected()) {
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
            final var ci = new ConnectionInfo(host, port);
            server = ConnectionProvider.connect(ci, IServerWorker.class);
            UnicastRemoteObject.exportObject(this, 0);
            server.register(uuid, this, Runtime.getRuntime().availableProcessors());
        } catch (RemoteException | ServerUnavailableException e) {
            return false;
        } catch (AlreadyRegisteredException e) {
            System.err.println(e.getCause());
            System.exit(0);
        }
        monitor = new ConnectionMonitor(server, Configuration.WORKER_PING_INTERVAL, uuid);
        monitor.addEventListener(new ConnectionListener() {
            @Override
            public void onConnected() {
                System.out.println("Connected!!!");
            }

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
        monitor.start();
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

    @Override
    public void unreferenced() {
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
        }
    }

    @Override
    public FileUploadHandle uploadJob(UUID userUUID, UUID jobUUID) throws RemoteException {
        System.out.printf("Job %s received from %s", jobUUID, userUUID);
        return null;
    }
}

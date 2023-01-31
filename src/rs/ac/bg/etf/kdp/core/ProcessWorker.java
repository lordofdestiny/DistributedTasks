package rs.ac.bg.etf.kdp.core;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class ProcessWorker implements IRMIProcessWorker {
    private IRMICentralServer server = null;
    private static final String format = "yyyy-MM-dd HH:mm:ss";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(format);

    @Override
    public void ping() {
        System.out.printf("Ping from server at %s!\n", dateFormat.format(new Date()));
    }

    private void connect(String host, int port) {
        try {
            UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (IRMICentralServer) registry.lookup("/CentralServer");
            server.registerWorker(UUID.randomUUID(), this);

        } catch (RemoteException | NotBoundException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) {
        ProcessWorker pw = new ProcessWorker();
        pw.connect("localhost", 8080);
    }
}

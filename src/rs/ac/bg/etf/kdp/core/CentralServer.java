package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.PropertyLoader;

import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.*;

public class CentralServer extends UnicastRemoteObject implements IRMICentralServer {
    static {
        try {
            PropertyLoader.loadConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CentralServer(int port) throws RemoteException {
        try {
            final var registry = LocateRegistry.createRegistry(port);
            registry.rebind("/CentralServer", this);
            System.out.println("Central server started");
            CentralServer.setLog(System.out);
        } catch (RemoteException e) {
            System.err.println("Failed to start central server");
            System.err.println(e.getMessage());
            System.exit(0);
        }
        final var timer = new Timer();
        timer.schedule(new WorkerMonitor(this), 0, 3000);
    }

    protected final Map<UUID, WorkerRecord> registeredWorkers = new ConcurrentHashMap<>();
    protected final Set<UUID> onlineWorkers = new ConcurrentSkipListSet<>();

    @Override
    public void registerWorker(UUID id, IRMIProcessWorker worker) throws RemoteException {
        final var record = new WorkerRecord(id, worker);
        registeredWorkers.put(id, record);
        try {
            worker.ping();
            System.out.printf("Worker %s is online!\n", id);
            record.setOnline();
        } catch (Exception e) {
            System.out.printf("Worker %s failed and is offline!\n", id);
        }
    }

    @Override
    public void ping(UUID id) throws RemoteException {
        registeredWorkers.get(id).setOnline();
        onlineWorkers.add(id);
    }

    public static void main(String[] args) {
        try {
            CentralServer cs = new CentralServer(8080);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}

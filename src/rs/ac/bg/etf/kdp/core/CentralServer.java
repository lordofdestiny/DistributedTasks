package rs.ac.bg.etf.kdp.core;

import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;

public class CentralServer extends UnicastRemoteObject implements IRMICentralServer {
    static {
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "10000");
    }
    private static class WorkerRecord {
        public UUID id;
        public IRMIProcessWorker worker;

        WorkerRecord(UUID id, IRMIProcessWorker worker) {
            this.id = id;
            this.worker = worker;
        }
    }

    public CentralServer(int port) throws RemoteException {
        super();
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
    }

    private final Map<UUID, WorkerRecord> registeredWorkers = new HashMap<>();
    private final Set<UUID> onlineWorkers = new HashSet<>();

    @Override
    public void registerWorker(UUID id, IRMIProcessWorker worker) throws RemoteException {
        registeredWorkers.put(id, new WorkerRecord(id, worker));
        try {
            worker.ping();
            onlineWorkers.add(id);
            System.out.printf("Worker %s is online%n", id);
        } catch (Exception e) {
            System.out.printf("Worker %s failed is offline%n", id);
        }
    }

    public static void main(String[] args) {
        CentralServer cs;
        try {
            cs = new CentralServer(8080);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        //noinspection InfiniteLoopStatement
        while (true) {
            // Possibly replace with thread pool that talks to a queue
            final var threads = new HashMap<UUID, Thread>(cs.onlineWorkers.size());
            for (final var wid : cs.onlineWorkers) {
                final var worker = cs.registeredWorkers.get(wid).worker;
                final var finalCs = cs;
                final var thread = new Thread(() -> {
                    try {
                        worker.ping();
                    } catch (RemoteException e) {
                        finalCs.onlineWorkers.remove(wid);
                        System.out.printf("Worker %s failed and is offline: %n", wid);
                    }
                });
                threads.put(wid, thread);
                thread.start();
            }
            for (final var entry : threads.entrySet()) {
                try {
                    entry.getValue().join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

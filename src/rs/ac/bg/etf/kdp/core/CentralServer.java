package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.PropertyLoader;

import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;

public class CentralServer extends UnicastRemoteObject implements IRMICentralServer {
    static {
        try {
            PropertyLoader.loadConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class WorkerRecord {
        public UUID id;
        public IRMIProcessWorker handle;
        private boolean online;
        private long lastOnlineTimestamp;

        WorkerRecord(UUID id, IRMIProcessWorker worker) {
            this.id = id;
            this.handle = worker;
            this.online = true;
        }

        public boolean isOnline() {
            return online;
        }

        public void setOnline() {
            this.online = true;
            lastOnlineTimestamp = System.currentTimeMillis();
        }

        public void setOffline() {
            this.online = false;
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
        final var record = new WorkerRecord(id, worker);
        registeredWorkers.put(id, record);
        try {
            worker.ping();
            onlineWorkers.add(id);
            System.out.printf("Worker %s is online!\n", id);
            record.setOnline();
        } catch (Exception e) {
            System.out.printf("Worker %s failed and is offline!\n", id);
        }
    }

    @Override
    public void ping(UUID id) throws RemoteException {
        onlineWorkers.add(id);
        registeredWorkers.get(id).setOnline();
    }

    public static void main(String[] args) {
        CentralServer cs;
        try {
            cs = new CentralServer(8080);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
//        final var threadPool = Executors.newCachedThreadPool();


        new Thread(() -> {
            while (true) {
                // Possibly replace with thread pool that talks to a queue
                final var threads = new HashMap<UUID, Thread>(cs.onlineWorkers.size());
                for (final var workerRecord : cs.registeredWorkers.values()) {
                    final var worker = workerRecord.handle;
                    final var wid = workerRecord.id;
                    final var finalCs = cs;
                    final var thread = new Thread(() -> {
                        try {
                            final var start = System.currentTimeMillis();
                            worker.ping();
                            final var ping = System.currentTimeMillis() - start;
                            if (workerRecord.isOnline()) {
                                System.out.printf("Ping to %s is %d ms\n", wid, ping);
                            } else {
                                System.out.printf("Worker %s is online again! Ping %d ms\n", wid, ping);
                                workerRecord.setOnline();
                            }
                        } catch (RemoteException e) {
                            finalCs.onlineWorkers.remove(wid);
                            if (workerRecord.isOnline()) {
                                workerRecord.setOffline();
                                System.out.printf("Worker %s failed and is offline!\n", workerRecord.id);
                            }
                            if(System.currentTimeMillis() - workerRecord.lastOnlineTimestamp > 10*1000) {
                                finalCs.registeredWorkers.remove(wid);
                            }
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
                try {
                    //noinspection BusyWait
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}

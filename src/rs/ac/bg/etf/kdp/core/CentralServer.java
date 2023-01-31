package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.PropertyLoader;

import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

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
        }

        public void setOffline() {
            this.online = false;
        }

        public Optional<Long> ping() {
            try {
                final var start = System.currentTimeMillis();
                handle.ping();
                final var end = System.currentTimeMillis();
                return Optional.of(end - start);
            } catch (RemoteException e) {
                return Optional.empty();
            }
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

    private final Map<UUID, WorkerRecord> registeredWorkers = new ConcurrentHashMap<>();
    private final Set<UUID> onlineWorkers = new ConcurrentSkipListSet<>();

    @Override
    public void registerWorker(UUID id, IRMIProcessWorker worker) throws RemoteException {
        final var record = new WorkerRecord(id, worker);
        registeredWorkers.put(id, record);
        try {
            worker.ping();
            System.out.printf("Worker %s is online!\n", id);
            record.setOnline();
        } catch (Exception e) {
            System.err.printf("Worker %s failed and is offline!\n", id);
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

        new Thread(() -> {
            while (true) {
                // Possibly replace with thread pool that talks to a queue
                final var threads = cs.registeredWorkers.values().stream()
                        .map((worker) ->(Runnable) () -> {

                                }
                        ).map(Thread::new).toArray(Thread[]::new);
                for (final var thead : threads) {
                    try {
                        thead.join();
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

    private void workerMonitor(WorkerRecord worker) {
        final var wid = worker.id;
        final var lastOnline = worker.isOnline();
        final var ping = worker.ping();
        if (ping.isPresent()) {
            System.out.printf(lastOnline
                            ? "Ping to %s is %d ms\n"
                            : "Worker %s is online again! Ping %d ms\n",
                    wid, ping.get());
        } else {
            onlineWorkers.remove(wid);
            if (worker.isOnline()) {
                System.err.printf("Worker %s failed and is offline!\n", wid);
            }
            worker.setOffline();
        }
    }
}

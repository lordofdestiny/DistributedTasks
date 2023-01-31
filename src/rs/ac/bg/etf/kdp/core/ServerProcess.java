package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.PropertyLoader;

import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class ServerProcess extends UnicastRemoteObject implements IRMIServerProcess {
    static {
        try {
            PropertyLoader.loadConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class WorkerRecord {
        public UUID id;
        public IRMIWorkerProcess handle;
        private boolean online;

        WorkerRecord(UUID id, IRMIWorkerProcess worker) {
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

    private final Map<UUID, WorkerRecord> registeredWorkers = new ConcurrentHashMap<>();
    private final Set<UUID> onlineWorkers = new ConcurrentSkipListSet<>();
    private static final String SERVER_ROUTE = System.getProperty("server.route");
    private static final int SERVER_PORT = Integer.parseInt(System.getProperty("server.port"));
    private static final int SERVER_PING_INTERVAL = Integer.parseInt(System.getProperty("server.pingInterval"));

    public ServerProcess() throws RemoteException {
        super();
        try {
            final var registry = LocateRegistry.createRegistry(SERVER_PORT);
            registry.rebind(SERVER_ROUTE, this);
            System.out.printf("Server started on port %s", SERVER_PORT);
            ServerProcess.setLog(System.out);
        } catch (RemoteException e) {
            System.err.println("Failed to start central server!");
            System.err.println(e.getMessage());
            System.exit(0);
        }
    }

    @Override
    public void registerWorker(UUID id, IRMIWorkerProcess worker) throws RemoteException {
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
        ServerProcess cs;
        try {
            cs = new ServerProcess();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        new Thread(cs::monitorWorkers).start();
    }

    private void monitorWorkers() {
        while (true) {
            // Possibly replace with thread pool that talks to a queue
            final var threads = registeredWorkers.values().stream()
                    .map((worker) -> (Runnable) () -> workerMonitor(worker))
                    .map(Thread::new).toArray(Thread[]::new);
            Arrays.stream(threads).forEach(Thread::start);
            try {
                for (final var thead : threads) {
                    thead.join();
                }
                //noinspection BusyWait
                Thread.sleep(SERVER_PING_INTERVAL);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
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

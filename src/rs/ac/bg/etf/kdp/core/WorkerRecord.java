package rs.ac.bg.etf.kdp.core;

import java.rmi.RemoteException;
import java.util.Optional;
import java.util.UUID;

class WorkerRecord {
    private final UUID id;
    private final IRMIProcessWorker handle;
    private boolean online;

    WorkerRecord(UUID id, IRMIProcessWorker worker) {
        this.id = id;
        this.handle = worker;
        this.online = true;
    }

    UUID getId() {
        return id;
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

    public Optional<Long> getPing() {
        try {
            final var start = System.currentTimeMillis();
            handle.ping();
            final var ping = System.currentTimeMillis() - start;
            return Optional.of(ping);
        } catch (RemoteException e) {
            return Optional.empty();
        }
    }
}

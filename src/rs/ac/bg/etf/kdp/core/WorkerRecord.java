package rs.ac.bg.etf.kdp.core;

import java.rmi.RemoteException;
import java.util.Optional;
import java.util.UUID;

class WorkerRecord {
    public UUID id;
    public IWorkerServer handle;
    private boolean online;

    WorkerRecord(UUID id, IWorkerServer worker) {
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
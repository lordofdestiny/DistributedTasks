package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IServerWorker extends IPingable, IUUIDPingable, Remote {
    void register(UUID id, IWorkerServer worker) throws RemoteException;
}

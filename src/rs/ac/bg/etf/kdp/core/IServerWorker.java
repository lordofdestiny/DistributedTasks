package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.ConnectionMonitor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IServerWorker extends ConnectionMonitor.Pingable, Remote {
    void register(UUID id, IWorkerServer worker) throws RemoteException;
}

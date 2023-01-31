package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IRMIServerProcess extends Remote {
    void registerWorker(UUID id , IRMIWorkerProcess worker) throws RemoteException;
    void ping(UUID id) throws RemoteException;
}

package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IRMICentralServer extends Remote {
    void registerWorker(UUID id , IRMIProcessWorker worker) throws RemoteException;
}

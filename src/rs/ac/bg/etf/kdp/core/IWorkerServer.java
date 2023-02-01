package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IWorkerServer extends Remote {
    void ping() throws RemoteException;
}

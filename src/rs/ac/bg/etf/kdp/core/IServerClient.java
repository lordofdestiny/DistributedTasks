package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IServerClient extends IPingable, IUUIDPingable, Remote {
    void register(UUID id, IClientServer client) throws RemoteException;

    void ping(UUID id) throws RemoteException;

    void ping() throws RemoteException;
}

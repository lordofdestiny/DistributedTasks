package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.ConnectionMonitor;

import java.rmi.*;
import java.util.UUID;

public interface IServerClient extends ConnectionMonitor.Pingable, Remote {
    void register(UUID id, IClientServer client) throws RemoteException;
    void ping(UUID id) throws RemoteException;
}

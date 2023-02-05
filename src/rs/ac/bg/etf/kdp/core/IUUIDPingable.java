package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IUUIDPingable extends Remote {
	void ping(UUID uuid) throws RemoteException;
}

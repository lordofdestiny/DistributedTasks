package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IWorkerServer extends IPingable, Remote {
	void ping() throws RemoteException;
}

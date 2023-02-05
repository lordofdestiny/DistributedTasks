package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.FileUploadHandle;

public interface IServerClient extends IPingable, IUUIDPingable, Remote {
	void register(UUID id, IClientServer client) throws RemoteException;

	void ping(UUID id) throws RemoteException;

	void ping() throws RemoteException;

	FileUploadHandle registerJob(UUID userUUID) throws RemoteException;
}
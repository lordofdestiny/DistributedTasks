package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.FileUploadHandle;

public interface IServerClient extends IPingable, IUUIDPingable, Remote {
	void register(UUID id, IClientServer client) throws RemoteException;

	void ping(UUID id) throws RemoteException;

	void ping() throws RemoteException;

	public static class ServerException extends Exception {
		ServerException(){
			super("Unspecified server exception occured");
		}

		ServerException(String msg) {
			super(msg);
		}
	}

	public static class UnregisteredClientException extends ServerException {
		public UnregisteredClientException() {
			super("Client with given ID was not registered!");
		}
	}

	public static class MultipleJobsException extends ServerException {
		public MultipleJobsException() {
			super("Job already registered. Wait of the first job to complete first.");
		}
	}

	FileUploadHandle registerJob(UUID userUUID) throws RemoteException, UnregisteredClientException, MultipleJobsException;
}
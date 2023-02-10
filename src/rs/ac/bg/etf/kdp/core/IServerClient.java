package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public interface IServerClient extends IPingable, IUUIDPingable, Remote {
	void register(UUID id, IClientServer client) throws RemoteException;

	void ping(UUID id) throws RemoteException;

	void ping() throws RemoteException;

	public static class ServerException extends Exception {
		private static final long serialVersionUID = 1L;

		ServerException() {
			super("Unspecified server exception occured");
		}

		ServerException(String msg) {
			super(msg);
		}
	}

	public static class UnregisteredClientException extends ServerException {
		private static final long serialVersionUID = 1L;

		public UnregisteredClientException() {
			super("Client with given ID was not registered!");
		}
	}

	public static class MultipleJobsException extends ServerException {
		private static final long serialVersionUID = 1L;

		public MultipleJobsException() {
			super("Job already registered. Wait of the first job to complete first.");
		}
	}

	void unregister(UUID id) throws RemoteException, UnregisteredClientException;

	FileUploadHandle registerJob(UUID userUUID)
			throws RemoteException, UnregisteredClientException, MultipleJobsException, RemoteIOException;
}
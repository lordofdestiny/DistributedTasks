package rs.ac.bg.etf.kdp.core;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Objects;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.FileUploadHandle;

public interface IServerWorker extends IPingable, IUUIDPingable, Remote {
	public static class AlreadyRegisteredException extends Exception {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public AlreadyRegisteredException() {
			super("Reconnection timeout has expired. Connection refused.");
		}
	}

	public class WorkerRegistration implements Serializable {
		private static final long serialVersionUID = 1L;

		private UUID uuid;
		private String name = "Unknown";
		private int concurrency = 1;
		private IWorkerServer handle;

		public WorkerRegistration(UUID uuid, IWorkerServer handle) {
			this.uuid = uuid;
			this.handle = handle;
		}

		public void setName(String name) {
			Objects.requireNonNull(name);
			this.name = name;
		}

		public void setConcurrency(int concurrency) {
			if (concurrency >= 1) {
				this.concurrency = concurrency;
			}
		}

		public UUID getUUID() {
			return uuid;
		}

		public String getName() {
			return name;
		}

		public int getConcurrency() {
			return concurrency;
		}

		public IWorkerServer getHandle() {
			return handle;
		}

	}

	void register(WorkerRegistration form) throws AlreadyRegisteredException, RemoteException;

	FileUploadHandle jobComplete(UUID workerUUID, UUID jobUUID) throws RemoteException;

	void reportJobFailedToStart(UUID workerUUID, UUID jobUUID) throws RemoteException;

	void reportJobFailed(UUID workerUUID, UUID jobUUID) throws RemoteException;
}

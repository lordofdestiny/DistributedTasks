package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

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

	
	void register(UUID id, IWorkerServer worker, int concurrecy) throws AlreadyRegisteredException,
			RemoteException;
}

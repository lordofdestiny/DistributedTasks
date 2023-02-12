package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IUUIDPingable extends Remote {
	public class UnknownUUIDException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public UnknownUUIDException() {
			super("Client or worker with this UUID is not registered!");
		}
	}

	static class ForcefullyUnbindException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public ForcefullyUnbindException() {
			super("This worker was previously considered failed and is ordered to reconnect!");
		}
	}

	void ping(UUID uuid) throws RemoteException;
}
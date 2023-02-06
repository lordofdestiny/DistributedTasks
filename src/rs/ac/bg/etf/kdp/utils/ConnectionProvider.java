package rs.ac.bg.etf.kdp.utils;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

public class ConnectionProvider {
	public static class ServerUnavailableException extends Exception {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public ServerUnavailableException() {
			super("Server is not online or bad credentials were provided!");
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T connect(ConnectionInfo info, Class<T> typeKey)
			throws ServerUnavailableException {
		try {
			final var registry = LocateRegistry.getRegistry(info.getIp(), info.getPort());
			final var server = registry.lookup(Configuration.SERVER_ROUTE);
			return (T) server;
		} catch (RemoteException | NotBoundException | ClassCastException e) {
			throw new ServerUnavailableException();
		}
	}
}
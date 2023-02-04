package rs.ac.bg.etf.kdp.utils;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

import rs.ac.bg.etf.kdp.gui.client.ServerUnavailableException;

public class ConnectionProvider {
	@SuppressWarnings("unchecked")
	public static <T> T connect(ConnectionInfo info, Class<T> typeKey) throws ServerUnavailableException {
		try {
			final var registry = LocateRegistry.getRegistry(info.getIp(), info.getPort());
			final var server = registry.lookup(Configuration.SERVER_ROUTE);
			return (T) server;
		} catch (RemoteException | NotBoundException | ClassCastException e) {
			throw new ServerUnavailableException();
		}
	}
}
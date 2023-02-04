package rs.ac.bg.etf.kdp.utils;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Optional;


public class ConnectionInfo {	
	public static class ConnectionProvider {		
		@SuppressWarnings("unchecked")
		public static <T> Optional<T> connect(ConnectionInfo info,Class<T> typeKey){
			try {
				final var registry = LocateRegistry.getRegistry(info.ip,info.port);
				final var server = registry.lookup(Configuration.SERVER_ROUTE);
				return Optional.of((T) server);
			}catch(RemoteException | NotBoundException | ClassCastException e) {
				return Optional.empty();
			}
		}
	}
	
	private String ip;
	private int port;
	
	public ConnectionInfo(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}
	public String getIp() {
		return ip;
	}
	public int getPort() {
		return port;
	}
}
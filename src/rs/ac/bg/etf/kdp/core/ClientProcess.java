package rs.ac.bg.etf.kdp.core;

import java.nio.file.Path;
import java.rmi.NoSuchObjectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rs.ac.bg.etf.kdp.gui.client.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionMonitor;
import rs.ac.bg.etf.kdp.utils.TransferListener;

public class ClientProcess implements IClientServer {
	private static final String SERVER_ROUTE = System.getProperty("server.route");
	private static final int SERVER_PORT = Integer.parseInt(System.getProperty("server.port"));
	private ConnectionMonitor monitor = null;
	private final ConnectionInfo conectionInfo;
	private final UUID uuid;
	private IServerClient server = null;
	
	public ClientProcess(UUID uuid, ConnectionInfo info) {
		this.uuid = uuid;
		this.conectionInfo = info;
	}

	public boolean connectToServer() throws ServerUnavailableException {
		return connectToServer(null);
	}

	public boolean connectToServer(List<ConnectionListener> listeners) throws ServerUnavailableException {
		try {
			UnicastRemoteObject.exportObject(this, 0);
			Registry registry = LocateRegistry.getRegistry(conectionInfo.getIp(), conectionInfo.getPort());
			server = (IServerClient) registry.lookup(SERVER_ROUTE);
			server.register(uuid, this);
		} catch (Exception e) {
			throw new ServerUnavailableException();
		}
		final var that = this;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				UnicastRemoteObject.unexportObject(that, true);
			} catch (NoSuchObjectException e) {
				throw new RuntimeException(e);
			}
		}));
		monitor = new ConnectionMonitor(server, 5000, uuid);
		if (listeners != null) {
			listeners.forEach(monitor::addEventListener);
		}
		monitor.start();
		return true;
	}

	public ConnectionMonitor getConnectionMonitor() {
		return monitor;
	}
	

	public void sendJob(Path zipFile, TransferListener cb) {
		
	}
	
	public void sendJob() {
		
	}

	public static void main(String[] args) {
		ClientProcess cp = new ClientProcess(UUID.randomUUID(), new ConnectionInfo("localhost", SERVER_PORT));
		final var listeners = new ArrayList<ConnectionListener>(1);
		listeners.add(new ConnectionListener() {
			@Override
			public void onConnected() {
				System.out.println("Connected!");
			}

			@Override
			public void onPingComplete(long ping) {
				System.out.printf("Ping: %d ms\n", ping);
			}

			@Override
			public void onConnectionLost() {
				System.err.println("Connection lost!");
			}

			@Override
			public void onReconnecting() {
				System.err.println("Reconnecting...");
			}

			@Override
			public void onReconnected(long ping) {
				System.out.printf("Reconnected, ping is %d ms\n", ping);
			}

			@Override
			public void onReconnectionFailed() {
				System.err.println("Reconnection failed!");
				System.exit(0);
			}
		});
		try {
			cp.connectToServer(listeners);
		} catch (ServerUnavailableException e) {
			System.err.println("Failed to connect to server!");
			e.printStackTrace();
			System.exit(0);
		}
	}

	@SuppressWarnings("unused")
	private class JobSender extends Thread {
		public JobSender(Path zip, TransferListener cb) {
			
		}
		
		@Override
		public void run() {
			// ask server to register a new job
			// send job to server
						
		}
	}
}

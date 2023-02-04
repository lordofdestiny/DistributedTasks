package rs.ac.bg.etf.kdp.core;

import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rs.ac.bg.etf.kdp.gui.client.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionMonitor;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.TransferListener;
import rs.ac.bg.etf.kdp.utils.UnicastUnexportHook;

public class ClientProcess implements IClientServer {
	private ConnectionMonitor monitor = null;
	private final ConnectionInfo conectionInfo;
	private final UUID uuid;
	private IServerClient server = null;

	public ClientProcess(UUID uuid, ConnectionInfo info) {
		this.uuid = uuid;
		this.conectionInfo = info;
	}

	public boolean connectToServer() {
		return connectToServer(null);
	}

	public boolean connectToServer(List<ConnectionListener> listeners) {
		try {
			server = ConnectionProvider.connect(conectionInfo, IServerClient.class);
			UnicastRemoteObject.exportObject(this, 0);
			server.register(uuid, this);
		} catch (RemoteException | ServerUnavailableException e) {
			return false;
		}
		Runtime.getRuntime().addShutdownHook(new UnicastUnexportHook(this));
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

	public static void main(String[] args) {
		final var cinfo = new ConnectionInfo("localhost", Configuration.SERVER_PORT);
		ClientProcess cp = new ClientProcess(UUID.randomUUID(), cinfo);
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
		if (!cp.connectToServer(listeners)) {
			System.err.println("Failed to connect to server!");
			System.exit(0);
		}
	}
}

package rs.ac.bg.etf.kdp.core.client;

import java.io.File;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.ConnectionMonitor;
import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.gui.client.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;

public class ClientProcess implements IClientServer, Unreferenced {
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

	public void submitJob(File zipFile, UploadingListener cb) {
		FileUploadHandle handle = null;
		try {
			handle = server.registerJob(ClientProcess.this.uuid);
		} catch (RemoteException e) {
			cb.onFailedConnection();
		}
		FileUploader uploader = new FileUploader(handle, zipFile, cb);
		uploader.start();
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

	@Override
	public void unreferenced() {
		try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
		}
	}
}

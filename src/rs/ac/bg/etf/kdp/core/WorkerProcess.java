package rs.ac.bg.etf.kdp.core;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import rs.ac.bg.etf.kdp.gui.client.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionMonitor;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.UnicastUnexportHook;

public class WorkerProcess implements IWorkerServer {
	static {
		Configuration.load();
	}

	private static final String DATE_FORMAT_STR = "dd.MM.YYYY. HH:mm:ss";
	private static final DateFormat df = new SimpleDateFormat(DATE_FORMAT_STR);
	private final UUID uuid = UUID.randomUUID();
	private final String host;
	private final int port;
	private IServerWorker server = null;
	private ConnectionMonitor connectionTracker;

	public WorkerProcess(String host, int port) {
		this.host = host;
		this.port = port;

	}

	@Override
	public void ping() {
		if (connectionTracker != null && connectionTracker.connected()) {
			System.out.printf("[%s]: Ping!\n", now());
		}
		try {
			server.ping(uuid);
		} catch (RemoteException e) {
			System.err.printf("[%s]: Lost connection to server!", now());
			System.err.println("Reconnecting...");
		}
	}

	private boolean connectToServer() {
		try {
			final var ci = new ConnectionInfo(host, port);
			server = ConnectionProvider.connect(ci, IServerWorker.class);			
			UnicastRemoteObject.exportObject(this, 0);
			server.register(uuid, this);
		} catch (RemoteException | ServerUnavailableException e) {
			return false;
		}
		Runtime.getRuntime().addShutdownHook(new UnicastUnexportHook(this));
		connectionTracker = new ConnectionMonitor(server, Configuration.SERVER_PING_INTERVAL, uuid);
		connectionTracker.addEventListener(new ConnectionListener() {
			@Override
			public void onConnected() {
				System.out.println("Connected!!!");
			}
			@Override
			public void onPingComplete(long ping) {
				System.out.printf("[%s]: Ping is %d ms\n", now(), ping);
			}

			@Override
			public void onConnectionLost() {
				System.err.println("Lost connection to server!");
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
				System.err.println("Could not reconnect to server! Exiting...");
				System.exit(0);
			}
		});
		connectionTracker.start();
		return true;
	}

	private static String now() {
		return df.format(new Date());
	}

	public static void main(String[] args) {
		WorkerProcess pw = new WorkerProcess("localhost", 8080);
		if (!pw.connectToServer()) {
			System.err.println("Failed to connect to server!");
			System.exit(0);
		}
	}
}

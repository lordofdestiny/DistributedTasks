package rs.ac.bg.etf.kdp.core;

import java.util.ArrayList;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.ConnectionListener;

public class ConnectionMonitor extends Thread {
	private final IPingable server;
	private final int interval;
	private long lastOnlineTime;
	private boolean connected = true;
	private final ArrayList<ConnectionListener> listeners = new ArrayList<>();

	public ConnectionMonitor(IPingable server, int pingInterval, UUID uuid) {
		super("ConnectionMonitor" + uuid);
		this.server = server;
		this.interval = pingInterval;
		addEventListener(new DefaultListener());
	}

	@Override
	public void run() {
		boolean first = true;
		while (true) {
			final var ping = IPingable.getPing(server);
			if (ping.isPresent()) {
				if (first) {
					listeners.forEach(ConnectionListener::onConnected);
					first = false;
				}
				// Ping successful
				listeners.forEach(l -> l.onPingComplete(ping.get()));
				try {
					// noinspection BusyWait
					Thread.sleep(interval);
				} catch (InterruptedException e) {
					if (Thread.interrupted())
						return;
				}
				continue;
			}
			// Ping failed
			listeners.forEach(ConnectionListener::onConnectionLost);
			reconnectToServer();
		}
	}

	private void reconnectToServer() {
		while (System.currentTimeMillis() - lastOnlineTime < 60 * 1000) {
			// Reconnecting
			listeners.forEach(ConnectionListener::onReconnecting);
			final var ping = IPingable.getPing(server);
			if (ping.isPresent()) {
				listeners.forEach(l -> l.onReconnected(ping.get()));
				return;
			}
		}
		// Reconnection failed
		listeners.forEach(ConnectionListener::onReconnectionFailed);
	}

	private synchronized void setConnected(boolean connected) {
		this.connected = connected;
	}

	public synchronized boolean connected() {
		return connected;
	}

	public void quit() {
		interrupt();
	}

	public void addEventListener(ConnectionListener listener) {
		listeners.add(listener);
	}

	public void removeEventListener(ConnectionListener listener) {
		listeners.remove(listener);
	}

	private class DefaultListener implements ConnectionListener {
		@Override
		public void onConnected() {
			setConnected(true);
		}

		@Override
		public void onPingComplete(long ping) {
			lastOnlineTime = System.currentTimeMillis();
		}

		@Override
		public void onConnectionLost() {
			setConnected(false);
		}

		@Override
		public void onReconnecting() {
		}

		@Override
		public void onReconnected(long ping) {
			setConnected(true);
		}

		@Override
		public void onReconnectionFailed() {
			setConnected(false);
			quit();
		}
	}
}

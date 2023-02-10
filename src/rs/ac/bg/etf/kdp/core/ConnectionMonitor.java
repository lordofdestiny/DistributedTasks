package rs.ac.bg.etf.kdp.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;

public class ConnectionMonitor extends Thread {
	private final IPingable server;
	private final int interval;
	private boolean connected = true;
	private final ArrayList<ConnectionListener> listeners = new ArrayList<>();

	public ConnectionMonitor(IPingable server, int pingInterval, UUID uuid) {
		super("ConnectionMonitor" + uuid);
		this.server = server;
		this.interval = pingInterval;
	}

	private class KillException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	}

	@Override
	public void run() {
		boolean first = true;
		while (true) {
			if (Thread.interrupted())
				return;
			final var ping = IPingable.getPing(server);
			if (Thread.interrupted())
				return;
			if (ping.isEmpty()) {
				// Ping failed
				setConnected(false);
				listeners.forEach(ConnectionListener::onConnectionLost);
				try {
					final var deadline = Instant.now()
							.plusSeconds(Configuration.SERVER_RECONNECTION_PERIOD);
					reconnectToServer(deadline);
					continue;
				} catch (KillException e) {
					return;
				}
			}
			if (first) {
				setConnected(true);
				listeners.forEach(ConnectionListener::onConnected);
				first = false;
			}
			// Ping successful
			listeners.forEach(l -> l.onPingComplete(ping.get()));
			try {
				// noinspection BusyWait
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				return;
			}

		}
	}

	private void reconnectToServer(Instant deadline) throws KillException {
		while (Instant.now().isBefore(deadline)) {
			if (Thread.interrupted()) {
				throw new KillException();
			}
			// Reconnecting
			listeners.forEach(ConnectionListener::onReconnecting);
			final var ping = IPingable.getPing(server);
			if (ping.isPresent()) {
				setConnected(true);
				listeners.forEach(l -> l.onReconnected(ping.get()));
				return;
			}
		}
		// Reconnection failed
		setConnected(false);
		listeners.forEach(ConnectionListener::onReconnectionFailed);
		quit();
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
}

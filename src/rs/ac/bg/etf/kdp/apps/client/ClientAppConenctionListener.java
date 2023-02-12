package rs.ac.bg.etf.kdp.apps.client;

import rs.ac.bg.etf.kdp.gui.client.ClientAppFrame;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;

class ClientAppConenctionListener implements ConnectionListener {
	private ClientAppFrame frame;

	public ClientAppConenctionListener(ClientAppFrame frame) {
		this.frame = frame;
	}

	@Override
	public void onConnected() {
		System.out.println("Connected!");
		frame.setConnected();
	}

	@Override
	public void onPingComplete(long ping) {
		System.out.println(String.format("Ping: %d ms", ping));
		frame.updatePing(ping);
	}

	@Override
	public void onConnectionLost() {
		System.err.println("Connection lost!");
		frame.setConnectionLost();
	}

	@Override
	public void onReconnecting() {
		System.err.println("Reconnecting...");
		frame.setReconnecting();
	}

	@Override
	public void onReconnected(long ping) {
		System.out.println(String.format("Reconnected, ping is %d ms", ping));
		frame.setReconnected(ping);
	}

	@Override
	public void onReconnectionFailed() {
		System.err.println("Reconnection failed!");
		frame.setReconnectionFailed();
		System.exit(0);
	}
}
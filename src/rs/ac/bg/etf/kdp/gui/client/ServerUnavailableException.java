package rs.ac.bg.etf.kdp.gui.client;

public class ServerUnavailableException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ServerUnavailableException() {
		super("Server is not online or bad credentials were provided!");
	}
}

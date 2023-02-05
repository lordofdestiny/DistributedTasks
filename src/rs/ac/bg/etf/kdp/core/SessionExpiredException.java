package rs.ac.bg.etf.kdp.core;

public class SessionExpiredException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public SessionExpiredException() {
		super("Reconnection timeout has expired. Connection refused.");
	}
}

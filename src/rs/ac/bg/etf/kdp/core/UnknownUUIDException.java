package rs.ac.bg.etf.kdp.core;

public class UnknownUUIDException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	UnknownUUIDException() {
        super("Client or worker with this UUID is not registered!");
    }
}

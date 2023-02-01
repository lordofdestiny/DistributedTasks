package rs.ac.bg.etf.kdp.core;

public class UnknownUUIDException extends RuntimeException {
    UnknownUUIDException() {
        super("Client or worker with this UUID is not registered!");
    }
}

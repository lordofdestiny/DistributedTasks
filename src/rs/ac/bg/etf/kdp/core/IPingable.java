package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Optional;

public interface IPingable extends Remote {
    void ping() throws RemoteException;
    
    default Optional<Long> getPing(){
    	try {
            final var start = System.currentTimeMillis();
            ping();
            final var end = System.currentTimeMillis();
            return Optional.of(end - start);
        } catch (RemoteException e) {
            return Optional.empty();
        }
    }
}

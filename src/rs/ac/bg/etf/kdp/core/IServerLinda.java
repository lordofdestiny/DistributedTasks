package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IServerLinda extends Remote {
	void out(String[] tuple) throws RemoteException;

	String[] in(String[] tuple) throws RemoteException;

	String[] inp(String[] tuple) throws RemoteException;

	String[] rd(String[] tuple) throws RemoteException;

	String[] rdp(String[] tuple) throws RemoteException;

	void eval(UUID userUUID, UUID mainJobUUID, String name, Runnable thread) throws Exception, RemoteException;

	void eval(UUID userUUID, UUID mainJobUUID, String className, Object[] construct, String methodName, Object[] arguments)
			throws Exception, RemoteException;
}

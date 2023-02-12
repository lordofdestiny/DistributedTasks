package rs.ac.bg.etf.kdp.core;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServerLinda extends Remote {
	void out(String[] tuple) throws RemoteException;

	String[] in(String[] tuple) throws RemoteException;

	String[] inp(String[] tuple) throws RemoteException;

	String[] rd(String[] tuple) throws RemoteException;

	String[] rdp(String[] tuple) throws RemoteException;

	void eval(JobAuthenticator auth, String name, Runnable thread) throws Exception, RemoteException;

	void eval(JobAuthenticator auth, String className, Object[] construct, String methodName, Object[] arguments)
			throws Exception, RemoteException;
}

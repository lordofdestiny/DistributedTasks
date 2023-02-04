package rs.ac.bg.etf.kdp.utils;

import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;

public class UnicastUnexportHook extends Thread {
	Remote object;
	public UnicastUnexportHook(Remote object) {
		this.object = object;
	}
	@Override
	public void run() {
		try {
			UnicastRemoteObject.unexportObject(object, true);
		} catch (NoSuchObjectException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}

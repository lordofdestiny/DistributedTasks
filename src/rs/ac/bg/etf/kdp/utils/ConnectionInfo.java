package rs.ac.bg.etf.kdp.utils;

public class ConnectionInfo {	
	private String ip;
	private int port;
	
	public ConnectionInfo(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}
	public String getIp() {
		return ip;
	}
	public int getPort() {
		return port;
	}
}
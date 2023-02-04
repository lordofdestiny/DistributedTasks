package rs.ac.bg.etf.kdp.utils;

import java.net.URL;

public class Configuration {
	public static final String SERVER_ROUTE;
	public static final int SERVER_PORT;
	public static final int SERVER_PING_INTERVAL;
	public static final int WORKER_PING_INTERVAL;
	static {
		URL resource;
		try {
			
			resource = new Configuration().getClass().getClassLoader().getResource("system.properties");
			System.getProperties().load(resource.openStream());
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			System.exit(1);
		}

		SERVER_ROUTE = loadString("server.route");
		SERVER_PORT = loadInt("server.port");
		SERVER_PING_INTERVAL = loadInt("server.pingInterval");
		WORKER_PING_INTERVAL = loadInt("worker.pingInterval");
	}

	public static void load() {
	}

	private static String loadString(String key) {
		return System.getProperty(key);
	}

	private static int loadInt(String key) {
		return Integer.valueOf(loadString(key));
	}
}

package rs.ac.bg.etf.kdp.utils;

public class Configuration {
	static {
		try {
			final var resource = new Configuration().getClass().getClassLoader()
					.getResource("system.properties");
			System.getProperties().load(resource.openStream());
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(1);
		}
	}

	public static final String SERVER_ROUTE = loadString("server.route");
	public static final int SERVER_PORT = loadInt("server.port");
	public static final int SERVER_PING_INTERVAL = loadInt("server.pingInterval");

	public static final int WORKER_PING_INTERVAL = loadInt("worker.pingInterval");
	public static final int WORKER_DEADLINE_INTERVAL = loadInt("server.workerDeadlineInterval");

	public static final int MAX_BUFFER_SIZE = loadInt("linda.maxBufferSize");

	public static final int SERVER_RECONNECTION_PERIOD = loadInt("worker.serverReconnectionPeriod");

	public static void load() {
	}

	private static String loadString(String key) {
		return System.getProperty(key);
	}

	private static int loadInt(String key) {
		return Integer.valueOf(loadString(key));
	}
}

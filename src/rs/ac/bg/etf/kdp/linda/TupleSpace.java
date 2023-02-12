package rs.ac.bg.etf.kdp.linda;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Objects;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IServerLinda;
import rs.ac.bg.etf.kdp.core.JobAuthenticator;

public class TupleSpace {
	private static String HOST_ADDRESS;
	private static String HOST_ROUTE;
	private static int HOST_PORT;
	private static UUID userUUID;
	private static UUID jobUUID;
	private static UUID parentJobUUID;
	private static UUID mainJobUUID;
	private static JobAuthenticator auth;

	private static boolean isRemoteCall() {
		final var rem = System.getenv("LINDA_REMOTE");
		return Boolean.parseBoolean(rem);
	}

	static {
		if (isRemoteCall()) {
			HOST_ADDRESS = System.getenv("LINDA_HOST");
			HOST_ROUTE = System.getenv("LINDA_ROUTE");
			HOST_PORT = Integer.valueOf(System.getenv("LINDA_PORT"));
			userUUID = UUID.fromString(System.getenv("LINDA_USER"));
			jobUUID = UUID.fromString(System.getenv("LINDA_THIS_JOB"));
			final var parent = System.getenv("LINDA_PARENT_JOB");
			parentJobUUID = parent.equals("0") ? null : UUID.fromString(parent);
			mainJobUUID = UUID.fromString(System.getenv("LINDA_MAIN_JOB"));
			auth = new JobAuthenticator(userUUID, mainJobUUID, parentJobUUID, jobUUID);
			System.setProperty("sun.rmi.transport.tcp.responseTimeout", "60000");
		}
	}

	private static void fill(String[] template, String[] data) {
		Objects.requireNonNull(template);
		Objects.requireNonNull(data);
		for (int i = 0; i < template.length; i++) {
			if (template[i] != null)
				continue;
			template[i] = String.valueOf(data[i]);
		}
	}

	public static Linda getLinda() throws RemoteException, NotBoundException {
		if (!isRemoteCall()) {
			return new LocalLinda();
		}
		final var registry = LocateRegistry.getRegistry(HOST_ADDRESS, HOST_PORT);
		final var server = (IServerLinda) registry.lookup(HOST_ROUTE);

		return new Linda() {
			private static final long serialVersionUID = 1L;

			@Override
			public void out(String[] tuple) {
				try {
					server.out(tuple);
				} catch (RemoteException e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to send data...");
				}
			}

			@Override
			public void in(String[] tuple) {
				try {
					final var result = server.in(tuple);
					fill(tuple, result);
				} catch (RemoteException e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to retreive response...");
				}
			}

			@Override
			public boolean inp(String[] tuple) {
				try {
					final var result = server.inp(tuple);
					if (result != null) {
						fill(tuple, result);
						return true;
					}
				} catch (RemoteException e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to retreive response...");
				}
				return false;
			}

			@Override
			public void rd(String[] tuple) {
				try {
					final var result = server.rd(tuple);
					fill(tuple, result);
				} catch (RemoteException e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to retreive response...");
				}
			}

			@Override
			public boolean rdp(String[] tuple) {
				try {
					final var result = server.rdp(tuple);
					if (result != null) {
						fill(tuple, result);
						return true;
					}
				} catch (RemoteException e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to retreive response...");
				}
				return false;
			}

			@Override
			public void eval(String name, Runnable thread) {
				try {
					server.eval(auth, name, thread);
					System.out.println(name + " started");
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to start new process...");
				}
			}

			@Override
			public void eval(String className, Object[] construct, String methodName,
					Object[] arguments) {
				try {
					server.eval(auth, className, construct, methodName, arguments);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to start new process...");
				}
			}

		};
	}
}

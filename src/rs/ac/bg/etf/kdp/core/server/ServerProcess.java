package rs.ac.bg.etf.kdp.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.core.IServerWorker;
import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.core.server.WorkerRecord.WorkerState;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.FileDownloader;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingListener;

public class ServerProcess extends UnicastRemoteObject implements IServerWorker, IServerClient {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	static final Path CHANGE_LATER_ROOT_DIR = Path.of("C:\\Users\\djumi\\Desktop\\Linda");

	static {
		Configuration.load();
	}
	private final Map<UUID, WorkerRecord> registeredWorkers = new ConcurrentHashMap<>();
	private final Map<UUID, ClientRecord> registeredClients = new ConcurrentHashMap<>();
	private final Map<UUID, JobRecord> allJobs = new ConcurrentHashMap<>();

	private WorkerMonitor monitor = null;
	private final WorkerStateListener defaultWorkerStateListener = new WorkerStateListener() {
		@Override
		public void workerFaied(WorkerRecord worker) {
			worker.setState(WorkerState.OFFLINE);
			System.err.printf("Worker %s failed and is offline!\n", worker.uuid);
			worker.setDeadline();
		}

		@Override
		public void reconnected(WorkerRecord worker, long ping) {
			worker.setState(WorkerState.OFFLINE);
			System.out.printf("Worker %s is online again! Ping %d ms\n", worker.uuid, ping);
		}

		@Override
		public void notConnected(WorkerRecord worker) {
			worker.setState(WorkerState.UNAVAILABLE);
			if (worker.deadlineExpired()) {
				registeredWorkers.remove(worker.getUUID());
				System.err.printf("Worker UUID %s invalidated for future uses!", worker.uuid);
			}
		}

		@Override
		public void isConnected(WorkerRecord worker, long ping) {
			worker.setState(WorkerState.ONLINE);
			System.out.printf("Ping to %s is %d ms\n", worker.uuid, ping);
		}
	};

	public ServerProcess() throws RemoteException {
		try {
			final var registry = LocateRegistry.createRegistry(Configuration.SERVER_PORT);
			registry.rebind(Configuration.SERVER_ROUTE, this);
			System.out.printf("Server started on port %s", Configuration.SERVER_PORT);
			monitor = new WorkerMonitor(() -> {
				return registeredWorkers.values().stream().toArray(WorkerRecord[]::new);
			}, Configuration.SERVER_PING_INTERVAL);
			monitor.addWorkerStateListener(defaultWorkerStateListener);
			ServerProcess.setLog(System.out);

			monitor.start();
		} catch (RemoteException e) {
			System.err.println("Failed to start central server!");
			System.err.println(e.getMessage());
			System.exit(0);
		}
	}

	@Override
	public void register(UUID id, IWorkerServer worker)
			throws AlreadyRegisteredException, RemoteException {
		if (registeredWorkers.containsKey(id)) {
			throw new AlreadyRegisteredException();
		}
		final var record = new WorkerRecord(id, worker);
		registeredWorkers.put(id, record);
		try {
			worker.ping();
			System.out.printf("Worker %s is online!\n", id);
		} catch (RuntimeException e) {
			record.setState(WorkerState.OFFLINE);
			System.err.printf("Worker %s failed and is offline!\n", id);
		}
	}

	@Override
	public void register(UUID id, IClientServer client) {
		final var record = new ClientRecord(id, client);
		registeredClients.put(id, record);
		System.out.printf("Client %s is online!\n", id);
	}

	@Override
	public void ping(UUID id) throws RemoteException {
		final var record = registeredWorkers.get(id);
		if (record != null) {
			record.setState(WorkerState.ONLINE);
		} else if (!registeredClients.containsKey(id)) {
			// Not a worker and not a client
			throw new UnknownUUIDException();
		}
	}

	@Override
	public void ping() {
		// Do nothing
	}

	@Override
	public FileUploadHandle registerJob(UUID userId) throws RemoteException {
		ClientRecord client = registeredClients.get(userId);
		if (client == null) {
			// throw unregistered user error
			return null;
		}
		if (client.hasRegisteredJob()) {
			// throw job already registered
			return null;
		}
		final var jobUUID = UUID.randomUUID();
		final var deadline = Instant.now().plus(2, ChronoUnit.MINUTES);
		JobRecord record = new JobRecord(userId, jobUUID, deadline);
		allJobs.put(jobUUID, record);

		final var downloader = new FileDownloader(record, new DownloadingListener() {
			private long total = 0;

			@Override
			public void onBytesReceived(int bytes) {
				total += bytes;
				System.out.printf("Job %s: Received %B so far...\n", record.jobUUID, total);
			}

			@Override
			public void onTransferComplete() {
				record.setStatus(JobRecord.JobStatus.READY);
				// Job is ready to be sent or whatever we want
				// Handle by some other internal thread
				// Maybe signal it somehow
				System.out.printf("Job %s received. Size: %dKB\n", record.jobUUID, total / 1024);
			}

			@Override
			public void onDeadlineExceeded() {
				System.err.printf("Failed to receive job from %d.\n", record.userUUID);
				allJobs.remove(record.jobUUID);
				try {
					Files.walk(record.rootDir).sorted(Comparator.reverseOrder()).map(Path::toFile)
							.forEach(File::delete);
				} catch (IOException e) {
					System.err.println("Failed to cleanup after failed job transfer");
				}
			}
		});

		return new FileUploadHandle(downloader, deadline);
	}

	public static void main(String[] args) {
		try {
			@SuppressWarnings("unused")
			ServerProcess cs = new ServerProcess();
		} catch (RemoteException e) {
			throw new RuntimeException(e);
		}
		// Thread or ThreadPool that manages job operations
	}
}

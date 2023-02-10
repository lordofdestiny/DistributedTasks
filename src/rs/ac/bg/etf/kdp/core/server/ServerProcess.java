package rs.ac.bg.etf.kdp.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.core.IServerLinda;
import rs.ac.bg.etf.kdp.core.IServerWorker;
import rs.ac.bg.etf.kdp.core.server.ServerJobRecord.JobStatus;
import rs.ac.bg.etf.kdp.core.server.WorkerRecord.WorkerState;
import rs.ac.bg.etf.kdp.linda.CentralizedLinda;
import rs.ac.bg.etf.kdp.linda.Linda;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.FileDownloader;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingToken;
import rs.ac.bg.etf.kdp.core.IWorkerServer.JobShardArgs;

public class ServerProcess extends UnicastRemoteObject
		implements IServerWorker, IServerClient, IServerLinda {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	static final Path CHANGE_LATER_ROOT_DIR = FileSystemView.getFileSystemView().getHomeDirectory()
			.toPath().resolve("Linda");

	static {
		Configuration.load();
	}

	private final Map<UUID, WorkerRecord> registeredWorkers = new ConcurrentHashMap<>();
	private final Map<UUID, ClientRecord> registeredClients = new ConcurrentHashMap<>();
	private final Map<UUID, ServerJobRecord> allJobs = new ConcurrentHashMap<>();

	private final ExecutorService jobUploadThreadPool = Executors.newFixedThreadPool(5);

	private final JobScheduler scheduler = new JobScheduler();

	private final Linda linda = new CentralizedLinda();

	public ServerProcess() throws RemoteException {
		try {
			final var registry = LocateRegistry.createRegistry(Configuration.SERVER_PORT);
			registry.rebind(Configuration.SERVER_ROUTE, this);
			scheduler.setHandler((worker, job) -> {
				jobUploadThreadPool.submit(() -> jobReadyHandle(job, worker));
			});
			scheduler.start();

			System.out.printf("Server started on port %s", Configuration.SERVER_PORT);
			ServerProcess.setLog(System.out);
		} catch (RemoteException e) {
			System.err.println("Failed to start central server!");
			System.err.println(e.getMessage());
			System.exit(0);
		}
	}

	@Override
	public void register(WorkerRegistration registration)
			throws AlreadyRegisteredException, RemoteException {
		Objects.requireNonNull(registration);
		if (registeredWorkers.containsKey(registration.getUUID())) {
			throw new AlreadyRegisteredException();
		}
		final var record = new WorkerRecord(registration);
		record.initializeMonitor(new WorkerStateListener() {
			private final String DATE_FORMAT_STR = "dd.MM.YYYY. HH:mm:ss";
			private final DateFormat df = new SimpleDateFormat(DATE_FORMAT_STR);

			private String now() {
				return df.format(new Date());
			}

			@Override
			public void workerUnavailable(WorkerRecord worker) {
				worker.setState(WorkerState.UNAVAILABLE);
				worker.setDeadline();
				System.err.printf("[%s] Worker %s unavailable!\n", now(), worker.getUUID());
			}

			@Override
			public void reconnected(WorkerRecord worker, long ping) {
				worker.setState(WorkerState.ONLINE);
				scheduler.putWorker(record);
				System.out.printf("[%s] Worker %s is online again! Ping %d ms\n", now(),
						worker.getUUID(), ping);
			}

			@Override
			public void workerFailed(WorkerRecord worker) {
				if (worker.isOnline()) {
					worker.setState(WorkerState.OFFLINE);
					System.err.printf("[%s] Worker UUID %s is offline!", now(), worker.getUUID());
				}
			}

			@Override
			public void isConnected(WorkerRecord worker, long ping) {
				worker.setState(WorkerState.ONLINE);
				System.out.printf("[%s] Ping to %s is %d ms\n", now(), worker.getUUID(), ping);
			}
		}, Configuration.SERVER_PING_INTERVAL);

		registeredWorkers.put(record.getUUID(), record);
		scheduler.putWorker(record);
		System.out.printf("Worker %s is online!\n", record.getUUID());
	}

	@Override
	public void register(UUID id, IClientServer clientHandle) {
		final var client = registeredClients.get(id);
		if (client != null) {
			client.online = true;
		} else {
			final var record = new ClientRecord(id, clientHandle);
			registeredClients.put(id, record);
		}
		System.out.printf("Client %s is online!\n", id);
	}

	@Override
	public void unregister(UUID id) throws RemoteException, UnregisteredClientException {
		ClientRecord client = registeredClients.get(id);
		if (client == null) {
			throw new UnregisteredClientException();
		}
		client.online = false;
		System.out.printf("Client %s is offline!\n", id);
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
	public FileUploadHandle registerJob(UUID userUUID) throws RemoteException,
			UnregisteredClientException, MultipleJobsException, RemoteIOException {
		ClientRecord client = registeredClients.get(userUUID);
		if (client == null) {
			throw new UnregisteredClientException();
		}
		if (client.hasRegisteredJob()) {
			throw new MultipleJobsException();
		}
		final var jobUUID = UUID.randomUUID();
		ServerMainJobRecord record = null;
		try {
			record = new ServerMainJobRecord(userUUID, jobUUID);
		} catch (IOException e) {
			throw new RemoteIOException(e);
		}
		client.mainJobUUID = jobUUID;
		allJobs.put(jobUUID, record);

		final var record0 = record;
		final var token = new DownloadingToken(record.getFileLocation(), 2, ChronoUnit.MINUTES);
		final var downloader = new FileDownloader(token, new DownloadingListener() {
			private long total = 0;

			@Override
			public void onBytesReceived(int bytes) {
				total += bytes;
				System.out.printf("Job %s: Received %B so far...\n", record0.jobUUID, total);
			}

			@Override
			public void onTransferComplete() {
				record0.setStatus(ServerJobRecord.JobStatus.READY);
				scheduler.putJob(record0);
				// Job is ready to be sent or whatever we want
				// Handle by some other internal thread
				// Maybe signal it somehow
				System.out.printf("Job %s received. Size: %dKB\n", record0.jobUUID, total / 1024);
			}

			@Override
			public void onDeadlineExceeded() {
				System.err.printf("Failed to receive job from %d.\n", userUUID);
				allJobs.remove(record0.jobUUID);
				client.mainJobUUID = null;
				try {
					Files.walk(record0.rootDir).sorted(Comparator.reverseOrder()).map(Path::toFile)
							.forEach(File::delete);
				} catch (IOException e) {
					System.err.println("Failed to cleanup after failed job transfer");
				}
			}
		});

		return new FileUploadHandle(downloader, token.deadline());
	}

	private void jobReadyHandle(ServerJobRecord job, WorkerRecord worker) {
		FileUploadHandle handle = null;
		try {
			handle = job.postToWorker(worker.getHandle());
		} catch (RemoteException | RemoteIOException e) {
			System.err.println("Failed to upload job, state restored...");
			scheduler.putWorker(worker);
			if (e instanceof RemoteIOException) {
				scheduler.putJob(job);
			}
			return;
		}
		if (!handle.isValid()) {
			// Worker already has the job files
			job.setStatus(JobStatus.RUNNING);
			return;
		}
		final var uploader = new FileUploader(handle, job.getFileLocation(),
				new UploadingListener() {
					@Override
					public void onDeadlineExceeded() {
						scheduler.putJob(job);
						System.out.println("Time limit exceeded! Check your connection");
					}

					@Override
					public void onIOException() {
						// Something wrong with the job, notify the user
						scheduler.putJob(job);
						scheduler.putWorker(worker); // Try again for other jobs
						System.err.printf("IOException on %s\n", worker.getUUID().toString());
					}

					@Override
					public void onUploadComplete(long bytes) {
						job.setStatus(JobStatus.RUNNING);
					}

					@Override
					public void onFailedConnection() {
						scheduler.putJob(job);
						System.err.println("Worker is not available! Try later!");
					}
				});
		uploader.start();
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

	@Override
	public void out(String[] tuple) {
		linda.out(tuple);
	}

	@Override
	public String[] in(String[] tuple) {
		linda.in(tuple);
		return tuple;
	}

	@Override
	public String[] inp(String[] tuple) {
		if (linda.inp(tuple)) {
			return tuple;
		} else {
			return null;
		}
	}

	@Override
	public String[] rd(String[] tuple) {
		linda.rd(tuple);
		return tuple;
	}

	@Override
	public String[] rdp(String[] tuple) {
		if (linda.rdp(tuple)) {
			return tuple;
		} else {
			return null;
		}
	}

	@Override
	public void eval(UUID userUUID, UUID mainJobUUID, String name, Runnable thread)
			throws Exception {
		final var client = registeredClients.get(userUUID);
		final var jobUUID = UUID.randomUUID();
		final var record = new ServerRunnableShardJobRecord(userUUID, mainJobUUID, jobUUID, name,
				thread);
		client.addShard(jobUUID);
		allJobs.put(jobUUID, record);
		scheduler.putJob(record);
	}

	@Override
	public void eval(UUID userUUID, UUID mainJobUUID, String className, Object[] construct,
			String methodName, Object[] arguments) throws Exception {
		final var client = registeredClients.get(userUUID);
		final var jobUUID = UUID.randomUUID();
		final var args = new JobShardArgs(className, construct, methodName, arguments);
		ServerJobRecord record = new ServerShardJobRecord(userUUID, mainJobUUID, jobUUID, args);
		client.addShard(jobUUID);
		allJobs.put(jobUUID, record);
		scheduler.putJob(record);
	}
}

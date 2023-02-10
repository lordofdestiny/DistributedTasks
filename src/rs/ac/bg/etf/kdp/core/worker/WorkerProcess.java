package rs.ac.bg.etf.kdp.core.worker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.MarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.ConnectionMonitor;
import rs.ac.bg.etf.kdp.core.IServerWorker;
import rs.ac.bg.etf.kdp.core.IServerWorker.WorkerRegistration;
import rs.ac.bg.etf.kdp.core.IServerWorker.AlreadyRegisteredException;
import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.FileDownloader;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingToken;;

public class WorkerProcess implements IWorkerServer, Unreferenced {
	static {
		Configuration.load();
	}

	public static final Path CHANGE_LATER_ROOT_DIR = FileSystemView.getFileSystemView()
			.getHomeDirectory().toPath().resolve("Jobs");

	private static final String DATE_FORMAT_STR = "dd.MM.YYYY. HH:mm:ss";
	private static final DateFormat df = new SimpleDateFormat(DATE_FORMAT_STR);

	private final ExecutorService jobCreationExecutor = Executors.newCachedThreadPool();
	private final CompletionService<UUID> jobCreationCompletionService = new ExecutorCompletionService<>(
			jobCreationExecutor);
	private final ExecutorService jobExecutor = Executors.newCachedThreadPool();
	private final ExecutorCompletionService<UUID> jobExecutionCompletionService = new ExecutorCompletionService<>(
			jobExecutor);

	private final Thread creationAwaiterThread = new Thread(this::jobCreationAwaiterLoop);
	private final Thread executionAwaiterThread = new Thread(this::jobExecutionAwaiterLoop);

	private final Map<UUID, WorkerJobRecord> registeredJobs = new ConcurrentHashMap<>();

	private final UUID uuid = UUID.randomUUID();
	private final String host;
	private final int port;
	private IServerWorker server = null;
	private ConnectionMonitor monitor;

	public WorkerProcess(String host, int port) {
		this.host = host;
		this.port = port;
		creationAwaiterThread.start();
		executionAwaiterThread.start();
	}

	@Override
	public void ping() {
		if (monitor != null && monitor.connected()) {
			System.out.printf("[%s]: Ping!\n", now());
		}
		try {
			server.ping(uuid);
		} catch (RemoteException e) {
			System.err.printf("[%s]: Lost connection to server!", now());
			System.err.println("Reconnecting...");
		}
	}

	private boolean connectToServer() {
		try {
			final var ci = new ConnectionInfo(host, port);
			server = ConnectionProvider.connect(ci, IServerWorker.class);
			UnicastRemoteObject.exportObject(this, 0);
			final var registration = new WorkerRegistration(uuid, this);
			final var concurrency = Runtime.getRuntime().availableProcessors();
			try {
				final var name = InetAddress.getLocalHost().getHostName();
				registration.setName(name);
			} catch (UnknownHostException ignore) {
			}
			registration.setConcurrency(concurrency);
			server.register(registration);
		} catch (RemoteException | ServerUnavailableException e) {
			return false;
		} catch (AlreadyRegisteredException e) {
			System.err.println(e.getCause());
			System.exit(0);
		}
		monitor = new ConnectionMonitor(server, Configuration.WORKER_PING_INTERVAL, uuid);
		monitor.addEventListener(new ConnectionListener() {
			@Override
			public void onConnected() {
				System.out.println("Connected!!!");
			}

			@Override
			public void onPingComplete(long ping) {
				System.out.printf("[%s]: Ping is %d ms\n", now(), ping);
			}

			@Override
			public void onConnectionLost() {
				System.err.println("Lost connection to server!");
			}

			@Override
			public void onReconnecting() {
				System.err.println("Reconnecting...");
			}

			@Override
			public void onReconnected(long ping) {
				System.out.printf("Reconnected, ping is %d ms\n", ping);
			}

			@Override
			public void onReconnectionFailed() {
				System.err.println("Could not reconnect to server! Exiting...");
				System.exit(0);
			}
		});
		monitor.start();
		return true;
	}

	private static String now() {
		return df.format(new Date());
	}

	@Override
	public void unreferenced() {
		try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
		}
	}

	private class WorkerDownloadingListener implements DownloadingListener {
		private WorkerJobRecord record;
		private Callable<UUID> task;

		WorkerDownloadingListener(WorkerJobRecord record, Callable<UUID> task) {
			this.record = record;
			this.task = task;
		}

		@Override
		public void onTransferComplete() {
			System.out.println("Received job...");
			jobCreationCompletionService.submit(task);
		}

		@Override
		public void onDeadlineExceeded() {
			System.err.printf("Failed to recieve job %s\n", record.getJobUUID());
			registeredJobs.remove(record.getJobUUID());
			try {
				FileOperations.deleteDirectory(record.getMainDirectory());
			} catch (IOException e) {
				System.out.println("Failed to cleanup!");
				e.printStackTrace();
			}
		}
	}

	private class ShardCreationTask implements Callable<UUID> {
		private WorkerJobRecord record;
		private Callable<JobTask> getJob;
		private boolean unzip;

		ShardCreationTask(WorkerJobRecord record, boolean unzip, Callable<JobTask> getJob) {
			this.unzip = unzip;
			this.getJob = getJob;
			this.record = record;
		}

		@Override
		public UUID call() throws Exception {
			if (unzip) {
				FileOperations.unzip(record.getZipLocation(), record.getMainDirectory());
				Files.delete(record.getZipLocation().toPath());
			}

			final var manifest = record.getMainDirectory().resolve("manifest.json").toFile();
			record.setJobDescriptor(JobDescriptor.parse(manifest));

			record.setTask(getJob.call());

			return record.getJobUUID();
		}

	}

	public FileUploadHandle makeFileUploadHandle(WorkerJobRecord record,
			Callable<UUID> shardCreationTask) throws RemoteException {
		final var token = new DownloadingToken(record.getZipLocation(), 2, ChronoUnit.MINUTES);
		final var listener = new WorkerDownloadingListener(record, shardCreationTask);
		final var downloader = new FileDownloader(token, listener);
		return new FileUploadHandle(downloader, token.deadline());
	}

	@Override
	public FileUploadHandle scheduleMainJob(UUID userUUID, UUID jobUUID)
			throws RemoteException, RemoteIOException {
		System.out.printf("Main job %s received\n", jobUUID);

		final var record = new WorkerJobRecord(userUUID, jobUUID);
		registeredJobs.put(jobUUID, record);

		try {
			record.createDirectories();
		} catch (IOException e) {
			throw new RemoteIOException(e);
		}

		final var shardCreationTask = new ShardCreationTask(record, true,
				() -> new MainJobTask(record));

		return makeFileUploadHandle(record, shardCreationTask);
	}

	@Override
	public FileUploadHandle scheduleJobShard(UUID userUUID, UUID mainJobUUID, UUID jobUUID,
			JobShardArgs args) throws RemoteException, MarshalException, UnmarshalException {
		System.out.printf("Class shard %s from %s received\n",jobUUID, mainJobUUID);

		final var record = new WorkerJobRecord(userUUID, mainJobUUID, jobUUID);
		registeredJobs.put(jobUUID, record);

		final var hasZip = registeredJobs.containsKey(mainJobUUID);

		final var shardCreationTask = new ShardCreationTask(record, !hasZip,
				() -> new ShardJobTask(record, args));

		if (hasZip) {
			jobCreationCompletionService.submit(shardCreationTask);
			return new FileUploadHandle();
		}

		return makeFileUploadHandle(record, shardCreationTask);
	}

	@Override
	public FileUploadHandle scheduleRunnableJobShard(UUID userUUID, UUID mainJobUUID, UUID jobUUID,
			String name, Runnable task) throws RemoteException {
		System.out.printf("Runnalbe shard %s from %s received\n",jobUUID, mainJobUUID);
		final var record = new WorkerJobRecord(userUUID, mainJobUUID, jobUUID);
		registeredJobs.put(jobUUID, record);

		final var hasZip = registeredJobs.containsKey(mainJobUUID);

		final var shardCreationTask = new ShardCreationTask(record, !hasZip,
				() -> new RunnableShardJobTask(record, name, task));

		if (hasZip) {
			jobCreationCompletionService.submit(shardCreationTask);
			return new FileUploadHandle();
		}

		return makeFileUploadHandle(record, shardCreationTask);
	}

	private void jobCreationAwaiterLoop() {
		while (true) {
			try {
				final var result = jobCreationCompletionService.take();
				try {
					final var jobUUID = result.get();
					final var record = registeredJobs.get(jobUUID);
					jobExecutionCompletionService.submit(() -> {
						record.getTask().getProcess().waitFor();
						return jobUUID;
					});
				} catch (Exception e) {
					// Notify server that job could not be started
					e.printStackTrace();
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void jobExecutionAwaiterLoop() {
		while (true) {
			try {
				final var result = jobExecutionCompletionService.take();
				try {
					final var jobUUID = result.get();
					System.out.println("Job complete");
					final var record = registeredJobs.get(jobUUID);
					record.generateResults();
					
					// Return files to server
				} catch (Exception e) {
					// Notify server that the job crashed
					e.printStackTrace();
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	public static void main(String[] args) {
		WorkerProcess pw = new WorkerProcess("localhost", 8080);
		if (!pw.connectToServer()) {
			System.err.println("Failed to connect to server!");
			System.exit(0);
		}
	}
}

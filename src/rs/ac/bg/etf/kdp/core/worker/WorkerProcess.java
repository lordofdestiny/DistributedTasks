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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.ConnectionMonitor;
import rs.ac.bg.etf.kdp.core.IServerWorker;
import rs.ac.bg.etf.kdp.core.IServerWorker.AlreadyRegisteredException;
import rs.ac.bg.etf.kdp.core.IServerWorker.WorkerRegistration;
import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.core.JobAuthenticator;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.FileDownloader;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingToken;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;;

public class WorkerProcess implements IWorkerServer, Unreferenced {
	static {
		Configuration.load();
	}

	public static final Path CHANGE_LATER_ROOT_DIR = FileSystemView.getFileSystemView()
			.getHomeDirectory().toPath().resolve("Jobs" + System.currentTimeMillis());

	{
		try {
			System.out.println(CHANGE_LATER_ROOT_DIR);
			Files.createDirectories(CHANGE_LATER_ROOT_DIR);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private final ExecutorService jobCreationExecutor = Executors.newCachedThreadPool();
	private final CompletionService<Void> jobCreationCompletionService = new ExecutorCompletionService<>(
			jobCreationExecutor);

	private final ExecutorService jobExecutor = Executors.newCachedThreadPool();
	private final ExecutorCompletionService<Void> jobExecutionCompletionService = new ExecutorCompletionService<>(
			jobExecutor);

	private final Thread creationAwaiterThread = new Thread(this::jobCreationAwaiterLoop);
	private final Thread executionAwaiterThread = new Thread(this::jobExecutionAwaiterLoop);

	private final Lock transactionLock = new ReentrantLock();

	private final Map<UUID, WorkerJobRecord> registeredJobs = new ConcurrentHashMap<>();

	private final Map<Future<Void>, WorkerJobRecord> futureOwners = new ConcurrentHashMap<>();

	private final BlockingQueue<WorkerJobRecord> jobsForUploading = new LinkedBlockingQueue<>();
	private final Thread jobResultUploadStarterThread = new Thread(this::jobUploadStarter);

	private UUID uuid = UUID.randomUUID();
	private ConnectionInfo connInfo;
	private IServerWorker server = null;
	private ConnectionMonitor monitor;

	public WorkerProcess(ConnectionInfo info) {
		this.connInfo = info;
		creationAwaiterThread.start();
		executionAwaiterThread.start();
		jobResultUploadStarterThread.start();
	}

	@Override
	public void ping() {
		if (monitor != null && monitor.connected()) {
			System.out.println("Ping!");
		}
		try {
			server.ping(uuid);
		} catch (RemoteException e) {
			System.err.println("Lost connection to server!");
			System.err.println("Reconnecting...");
		}
	}

	private final ConnectionListener connectionListener = new ConnectionListener() {
		@Override
		public void onConnected() {
			System.out.println("Connected!!!");
		}

		@Override
		public void onPingComplete(long ping) {
			System.out.println(String.format("Ping is %d ms", ping));
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
			System.out.println(String.format("Reconnected, ping is %d ms", ping));
		}

		@Override
		public void onReconnectionFailed() {
			System.err.println("Could not reconnect to server! Attempting to reconnect...");
			System.exit(0);
		}
	};

	public boolean connectToServer() {
		try {
			server = ConnectionProvider.connect(connInfo, IServerWorker.class);
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
		monitor.addEventListener(connectionListener);
		monitor.start();
		return true;
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
		private Callable<Void> task;

		WorkerDownloadingListener(WorkerJobRecord record, Callable<Void> task) {
			this.record = record;
			this.task = task;
		}

		@Override
		public void onTransferComplete() {
			System.out.println("Received job...");
			final var future = jobCreationCompletionService.submit(task);
			futureOwners.put(future, record);
		}

		@Override
		public void onDeadlineExceeded() {
			System.err.println(String.format("Failed to recieve job %s", record.getJobUUID()));
			registeredJobs.remove(record.getJobUUID());
			try {
				FileOperations.deleteDirectory(record.getMainDirectory());
			} catch (IOException e) {
				System.out.println("Failed to cleanup!");
				e.printStackTrace();
			}
		}
	}

	private class ShardCreationTask implements Callable<Void> {
		private WorkerJobRecord record;
		private Callable<JobTask> getJob;
		private boolean unzip;

		ShardCreationTask(WorkerJobRecord record, boolean unzip, Callable<JobTask> getJob) {
			this.unzip = unzip;
			this.getJob = getJob;
			this.record = record;
		}

		@Override
		public Void call() throws Exception {
			if (unzip) {
				FileOperations.unzip(record.getZipLocation(), record.getMainDirectory());
				Files.delete(record.getZipLocation().toPath());
			}

			final var manifest = record.getMainDirectory().resolve("manifest.json").toFile();
			record.setJobDescriptor(JobDescriptor.parse(manifest));

			record.setTask(getJob.call());
			return null;
		}

	}

	public FileUploadHandle makeFileUploadHandle(WorkerJobRecord record,
			Callable<Void> shardCreationTask) throws RemoteException {
		final var token = new DownloadingToken(record.getZipLocation(), 2, ChronoUnit.MINUTES);
		final var listener = new WorkerDownloadingListener(record, shardCreationTask);
		final var downloader = new FileDownloader(token, listener);
		return new FileUploadHandle(downloader, token.deadline());
	}

	@Override
	public FileUploadHandle scheduleMainJob(UUID userUUID, UUID jobUUID)
			throws RemoteException, RemoteIOException {
		System.out.println(String.format("Main job %s received", jobUUID));

		final var record = new WorkerJobRecord(userUUID, jobUUID);
		registeredJobs.put(jobUUID, record);

		try {
			record.createDirectories();
		} catch (IOException e) {
			throw new RemoteIOException(e);
		}

		final var shardCreationTask = new ShardCreationTask(record, true,
				() -> new MainJobTask(record, connInfo));

		return makeFileUploadHandle(record, shardCreationTask);
	}

	@Override
	public FileUploadHandle scheduleJobShard(JobAuthenticator auth, JobShardArgs args)
			throws RemoteException, MarshalException, UnmarshalException {
		System.out.println(
				String.format("Class shard %s from %s received", auth.jobUUID, auth.mainJobUUID));

		final var record = new WorkerJobRecord(auth.userUUID, auth.mainJobUUID, auth.parentJobUUID,
				auth.jobUUID);
		registeredJobs.put(auth.jobUUID, record);

		final var hasZip = registeredJobs.containsKey(auth.mainJobUUID);

		final var shardCreationTask = new ShardCreationTask(record, !hasZip,
				() -> new ClassShardJobTask(record, connInfo, args));

		if (hasZip) {
			final var future = jobCreationCompletionService.submit(shardCreationTask);
			futureOwners.put(future, record);
			return new FileUploadHandle();
		}

		return makeFileUploadHandle(record, shardCreationTask);
	}

	@Override
	public FileUploadHandle scheduleRunnableJobShard(JobAuthenticator auth, String name,
			Runnable task) throws RemoteException {
		System.out.println(String.format("Runnalbe shard %s from %s received", auth.jobUUID,
				auth.mainJobUUID));
		final var record = new WorkerJobRecord(auth.userUUID, auth.mainJobUUID, auth.parentJobUUID,
				auth.jobUUID);
		registeredJobs.put(auth.jobUUID, record);

		final var hasZip = registeredJobs.containsKey(auth.mainJobUUID);

		final var shardCreationTask = new ShardCreationTask(record, !hasZip,
				() -> new RunnableShardJobTask(record, connInfo, name, task));

		if (hasZip) {
			final var future = jobCreationCompletionService.submit(shardCreationTask);
			futureOwners.put(future, record);
			return new FileUploadHandle();
		}

		return makeFileUploadHandle(record, shardCreationTask);
	}

	private void jobCreationAwaiterLoop() {
		while (true) {
			try {
				final var future = jobCreationCompletionService.take();
				final var record = futureOwners.remove(future);
				if (record == null) {
					continue;
				}
				try {
					future.get();
					final var jobFuture = jobExecutionCompletionService.submit(() -> {
						record.getTask().getProcess().waitFor();
						return null;
					});
					futureOwners.put(jobFuture, record);
				} catch (Exception e) {
					final var jobUUID = record.getJobUUID();
					try {
						// Notify server that job could not be started
						server.reportJobFailedToStart(this.uuid, jobUUID);
					} catch (RemoteException e1) {
						System.err.println(String
								.format("Failed to notify server that task %s failed", jobUUID));
					}
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void jobExecutionAwaiterLoop() {
		while (true) {
			try {
				final var future = jobExecutionCompletionService.take();
				final var record = futureOwners.remove(future);
				if (record == null) {
					continue;
				}
				try {
					future.get();
					if (record.getTask().getProcess().exitValue() != 0) {
						throw new Exception("Job runtime failure");
					}
					System.out.println(String.format("Job %s complete", record.getJobUUID()));
					record.generateResults();
					registeredJobs.remove(record.getJobUUID());
					// Return files to server
					jobsForUploading.add(record);
				} catch (IOException e) {
					// HANDLE THIS SOMEHOW !!!
					System.err.println(String.format("Failed to return result for % to server",
							record.getJobUUID()));
				} catch (Exception e) {
					System.out.println(String.format("Job %s failed.", record.getJobUUID()));
					try {
						server.reportJobFailed(this.uuid, record.getJobUUID());
					} catch (RemoteException e1) {
						System.err.println(
								String.format("Failed to notify server that task %s failed",
										record.getJobUUID()));
					}
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void jobUploadStarter() {
		while (true) {
			WorkerJobRecord job = null;
			try {
				job = jobsForUploading.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			if (job.getUploadFailureCount() >= 5) {
				System.err.println(String.format("Giving up on sending %s", job.getJobUUID()));
				continue;
			}

			final var destination = job.getJobDirectory().resolve("results.zip");

			if (!destination.toFile().exists()) {
				try {
					FileOperations.zipDirectory(job.getResultsDir(), destination.toFile());
				} catch (IOException e) {
					System.err.println(String.format("Failed to create results.zip for %s...",
							job.getJobUUID()));
					job.countUpUploadFailures();
					if (!jobsForUploading.contains(job)) {
						jobsForUploading.add(job);
					}
					continue;
				}
			}

			FileUploadHandle handle = null;
			try {
				handle = server.jobComplete(this.uuid, job.getJobUUID());
			} catch (RemoteException e) {
				System.err.println(
						String.format("Failed to fetch upload handle for %s", job.getJobUUID()));
				if (!jobsForUploading.contains(job)) {
					jobsForUploading.add(job);
				}
			}

			if (!handle.isValid()) {
				while (jobsForUploading.remove(job)) {
					// Remove all instances of this job pending to upload
				}
			}

			final WorkerJobRecord Job = job;
			final var uploader = new FileUploader(handle, destination.toFile(),
					new UploadingListener() {
						public void onFailedConnection() {
							System.err.println(String.format(
									"Failed to connect to server while sending %s to server...",
									Job.getJobUUID()));
							if (!jobsForUploading.contains(Job)) {
								jobsForUploading.add(Job);
							}
						}

						public void onDeadlineExceeded() {
							System.err.println(
									String.format("Deadlin exceeded while sending %s to server...",
											Job.getJobUUID()));
							if (!jobsForUploading.contains(Job)) {
								jobsForUploading.add(Job);
							}
						}

						public void onIOException() {
							System.err.println(String.format(
									"File error while sending %s to server...", Job.getJobUUID()));
							if (!jobsForUploading.contains(Job)) {
								jobsForUploading.add(Job);
							}
						}

						public void onUploadComplete(long bytes) {
							System.out.println(String.format("Results for %s sent to server...",
									Job.getJobUUID()));
							try {
								while (jobsForUploading.remove(Job)) {
									// Remove all instances of this job pending to upload
								}
								Files.delete(destination);
							} catch (IOException e) {
								System.err
										.println(String.format("Failed to delete %s", destination));
							}
						}
					});
			uploader.start();

			if (Thread.interrupted()) {
				return;
			}
		}
	}

	public static void main(String[] args) {
		WorkerProcess pw = new WorkerProcess(new ConnectionInfo("localhost", 8080));
		if (!pw.connectToServer()) {
			System.err.println("Failed to connect to server!");
			System.exit(0);
		}
	}

	@Override
	public int killJobsAssociatedWithClient(UUID clientUUID) throws RemoteException {
		System.out.println("Destroying jobs for user " + clientUUID);
		transactionLock.lock();
		try {
			final Predicate<WorkerJobRecord> test = job -> job.getUserUUID().equals(clientUUID);
			int count = (int) registeredJobs.values().stream().filter(test).count();
			jobsForUploading.removeIf(test);
			for (final var future : futureOwners.keySet()) {
				future.cancel(true);
			}
			futureOwners.values().removeIf(test);
			registeredJobs.values().stream().filter(test).forEach(job -> {
				job.getTask().killProcess();
			});
			registeredJobs.values().removeIf(test);
			return count;
		} finally {
			transactionLock.unlock();
		}
	}
}

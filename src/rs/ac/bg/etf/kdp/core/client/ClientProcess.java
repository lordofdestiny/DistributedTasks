package rs.ac.bg.etf.kdp.core.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import rs.ac.bg.etf.kdp.core.ConnectionMonitor;
import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.core.IServerClient.MultipleJobsException;
import rs.ac.bg.etf.kdp.core.IServerClient.ResultRequestCode;
import rs.ac.bg.etf.kdp.core.IServerClient.UnregisteredClientException;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.FileDownloader;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingToken;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class ClientProcess implements IClientServer, Unreferenced {
	private ConnectionMonitor monitor = null;
	private final ConnectionInfo conectionInfo;
	private final UUID uuid;
	private IServerClient server = null;

	private Map<UUID, Integer> failCounters = new ConcurrentHashMap<>();
	private Path jobResultsDir;

	private ExecutorService asyncTaskExecutor = Executors.newCachedThreadPool();

	public ClientProcess(UUID uuid, ConnectionInfo info, Path jobResultsDirectory) {
		this.uuid = uuid;
		this.conectionInfo = info;
		this.jobResultsDir = jobResultsDirectory;
	}

	public boolean connectToServer() {
		return connectToServer(null);
	}

	public boolean connected() {
		return server != null;
	}

	public boolean connectToServer(ConnectionListener listener) {
		try {
			server = ConnectionProvider.connect(conectionInfo, IServerClient.class);
			UnicastRemoteObject.exportObject(this, 0);
			server.register(uuid, this);
		} catch (RemoteException | ServerUnavailableException e) {
			return false;
		}
		monitor = new ConnectionMonitor(server, 5000, uuid);
		if (listener != null) {
			monitor.addEventListener(listener);
		}
		monitor.start();
		return true;
	}

	public ConnectionMonitor getConnectionMonitor() {
		return monitor;
	}

	public FileUploader submitJob(File zipFile, UploadingListener cb)
			throws UnregisteredClientException, MultipleJobsException, RemoteIOException {
		FileUploadHandle handle = null;
		try {
			handle = server.registerJob(uuid);
		} catch (RemoteException e) {
			cb.onFailedConnection();
		}

		FileUploader uploader = new FileUploader(handle, zipFile, cb);
		uploader.start();
		return uploader;
	}

	private Consumer<String> failedToStartCallback = null;

	public void setFailedToStartListener(Consumer<String> cb) {
		failedToStartCallback = Objects.requireNonNull(cb);
	}

	@Override
	public void notifyJobFailedToStart(UUID jobUUID, String cause) throws RemoteException {
		if (failedToStartCallback != null) {
			failCounters.compute(jobUUID, (t, v) -> v == null ? 0 : v + 1);
			if (failCounters.get(jobUUID) >= 5) {
				System.err.println(String.format("Job %s will not be started anymore.", jobUUID));
			}
			failedToStartCallback.accept(String.format("Job % failed to start 5 times.", jobUUID));
		}
	}
	
	private Consumer<JobTreeNode> failedJobCallback = null;

	public void setJobFailedListener(Consumer<JobTreeNode> cb) {
		failedJobCallback = Objects.requireNonNull(cb);
	}

	@Override
	public void notifyJobFailed(JobTreeNode desc) throws RemoteException {
		// No clue if something else is necessary
		if (failedJobCallback != null) {
			failedJobCallback.accept(desc);
		}
	}

	public static interface JobResultsTransferListener {

		void onResultsReceived(Path location);

		void onTransferFailed();
	}

	private JobResultsTransferListener jobResultsTransferCallback = null;

	public void setJobResultsTransferListener(JobResultsTransferListener cb) {
		this.jobResultsTransferCallback = cb;
	}

	@Override
	public FileUploadHandle submitCompleteJob(UUID mainJobUUID) throws RemoteException {
		// Server can forward job tree so that descriptions can be extracted
		System.out.println(jobResultsDir);
		System.out.println("JOB COMPLETE, RECEIVING STARTED");
		final var destPath = jobResultsDir.resolve(mainJobUUID.toString());

		try {
			Files.createDirectories(destPath);
		} catch (IOException e) {
			return new FileUploadHandle();
		}

		final var destFile = destPath.resolve("all_results.zip").toFile();

		final var token = new DownloadingToken(destFile, 3, ChronoUnit.MINUTES);
		final var downloader = new FileDownloader(token, new DownloadingListener() {
			@Override
			public void onTransferComplete() {
				if (jobResultsTransferCallback != null) {
					asyncTaskExecutor.submit(() -> {
						jobResultsTransferCallback.onResultsReceived(destFile.toPath());
					});
				}
			}

			@Override
			public void onDeadlineExceeded() {
				if (jobResultsTransferCallback != null) {
					asyncTaskExecutor.submit(() -> {
						jobResultsTransferCallback.onTransferFailed();
					});
				}
			}
		});

		return new FileUploadHandle(downloader, token.deadline());
	}

	public ResultRequestCode requestResults() {
		try {
			return server.requestResults(uuid);
		} catch (RemoteException e) {
			System.err.println("Failed to request results from server!");
			return ResultRequestCode.UNKNOWN;
		}
	}
	
	public void respondToJobFailure(int response) {
		try {			
			server.respondToJobFailed(uuid, response);
		}catch(RemoteException e) {
			System.err.println("Failed to reply to server!");
		}
	}

	public void shutdown() {
		try {
			server.unregister(uuid);
		} catch (UnregisteredClientException | RemoteException e) {
			System.err.println("Failed to unregister at server!");
		}
		monitor.quit();
		unreferenced();
	}

	@Override
	public void unreferenced() {
		try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
		}
	}
}

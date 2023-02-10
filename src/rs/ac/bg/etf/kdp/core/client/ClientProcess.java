package rs.ac.bg.etf.kdp.core.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.filechooser.FileSystemView;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import rs.ac.bg.etf.kdp.core.ConnectionMonitor;
import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.core.IServerClient.MultipleJobsException;
import rs.ac.bg.etf.kdp.core.IServerClient.UnregisteredClientException;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;
import rs.ac.bg.etf.kdp.utils.JobDescriptor.JobCreationException;
import rs.ac.bg.etf.kdp.utils.FileOperations;

public class ClientProcess implements IClientServer, Unreferenced {
	private ConnectionMonitor monitor = null;
	private final ConnectionInfo conectionInfo;
	private final UUID uuid;
	private IServerClient server = null;

	public ClientProcess(UUID uuid, ConnectionInfo info) {
		this.uuid = uuid;
		this.conectionInfo = info;
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

	public static void main(String[] args) {
		final var cinfo = new ConnectionInfo("localhost", Configuration.SERVER_PORT);
		ClientProcess cp = new ClientProcess(UUID.randomUUID(), cinfo);
		AtomicBoolean online = new AtomicBoolean(false);
		final var listener = new ConnectionListener() {
			@Override
			public void onConnected() {
				online.set(true);
				System.out.println("Connected!");
			}

			@Override
			public void onPingComplete(long ping) {
				online.set(true);
				System.out.printf("Ping: %d ms\n", ping);
			}

			@Override
			public void onConnectionLost() {
				System.err.println("Connection lost!");
			}

			@Override
			public void onReconnecting() {
				System.err.println("Reconnecting...");
			}

			@Override
			public void onReconnected(long ping) {
				online.set(true);
				System.out.printf("Reconnected, ping is %d ms\n", ping);
			}

			@Override
			public void onReconnectionFailed() {
				System.err.println("Reconnection failed!");
				System.exit(0);
			}
		};
		if (!cp.connectToServer(listener)) {
			System.err.println("Failed to connect to server!");
			System.exit(0);
		}

		final var homeDir = FileSystemView.getFileSystemView().getHomeDirectory().toPath();
		final var filePath = homeDir.resolve("Lindica.json").toFile();
		Path tempDir = null;
		try {
			final var job = JobDescriptor.parse(filePath);
			tempDir = Files.createTempDirectory(homeDir, "linda_job-");
			final var copies = job.copyFilesToDirectory(tempDir);
			final var zipFile = tempDir.resolve("job.zip").toFile();
			FileOperations.zip(copies, zipFile);

			final var temp = tempDir;
			final var uploader = cp.submitJob(zipFile, new UploadingListener() {
				long bytesUploaded = 0;
				long totalSize = 0;
				long failCount = 0;
				{
					try {
						totalSize = Files.size(zipFile.toPath());
					} catch (IOException ignore) {
					}
					System.out.printf("Upload size: %.2fKB\n", totalSize / 1024.0);
				}

				@Override
				public void onBlockUploadFailed(int blockNo) {
					failCount += 1;
					System.out.printf("Block No. %d failed %d times\n", blockNo, failCount);
				}

				@Override
				public void onBytesUploaded(int bytes) {
					failCount = 0;
					bytesUploaded += bytes;
					System.out.printf("Transfered %dB so far.\n", bytesUploaded);
				}

				@Override
				public void onDeadlineExceeded() {
					System.err.println("Time limit exceeded! Check your connection");
					try {
						if (FileOperations.deleteDirectory(temp)) {
							System.out.println("All files deleted successfuly!");
						} else {
							System.out.println("Failed to delete some files!");
						}
					} catch (IOException e) {
						System.err.println("Failed while deleteing...");
					}
				}

				@Override
				public void onIOException() {
					System.err.println(
							"Files could not be read from disk. Try saving them on desktop!");
				}

				@Override
				public void onUploadComplete(long bytes) {
					try {
						if (FileOperations.deleteDirectory(temp)) {
							System.out.println("All files deleted successfuly!");
						} else {
							System.out.println("Failed to delete some files!");
						}
					} catch (IOException e) {
						System.err.println("Failed while deleteing...");
					}
				}

				@Override
				public void onFailedConnection() {
					System.err.println("Server is not available! Try later!");
				}
			});
			try {
				System.out.println("Waiting for upload to complete...");
				uploader.join();
				System.out.println("Upload complete!");
			} catch (InterruptedException e) {
				System.err.println("Unexpectedly interrupted!");
			}
			cp.shutdown();
		} catch (IOException | RemoteIOException | JsonSyntaxException | JsonIOException | JobCreationException
				| UnregisteredClientException | MultipleJobsException e) {
			System.out.println("Failed to do stuff");
			if (tempDir != null) {
				try {
					FileOperations.deleteDirectory(tempDir);
				} catch (IOException e1) {
					System.err.println("Failed to delete temporary files!");
				}
			}
			e.printStackTrace();
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

package rs.ac.bg.etf.kdp.core.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.swing.filechooser.FileSystemView;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import rs.ac.bg.etf.kdp.core.ConnectionMonitor;
import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;
import rs.ac.bg.etf.kdp.utils.JobDescriptorIOOperations;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;
import rs.ac.bg.etf.kdp.utils.JobDescriptor.JobCreationException;

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

	public boolean connectToServer(List<ConnectionListener> listeners) {
		try {
			server = ConnectionProvider.connect(conectionInfo, IServerClient.class);
			UnicastRemoteObject.exportObject(this, 0);
			server.register(uuid, this);
		} catch (RemoteException | ServerUnavailableException e) {
			return false;
		}
		monitor = new ConnectionMonitor(server, 5000, uuid);
		if (listeners != null) {
			listeners.forEach(monitor::addEventListener);
		}
		monitor.start();
		return true;
	}

	public ConnectionMonitor getConnectionMonitor() {
		return monitor;
	}

	public FileUploader submitJob(File zipFile, UploadingListener cb) {
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
		final var listeners = new ArrayList<ConnectionListener>(1);
		listeners.add(new ConnectionListener() {
			@Override
			public void onConnected() {
				System.out.println("Connected!");
			}

			@Override
			public void onPingComplete(long ping) {
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
				System.out.printf("Reconnected, ping is %d ms\n", ping);
			}

			@Override
			public void onReconnectionFailed() {
				System.err.println("Reconnection failed!");
				System.exit(0);
			}
		});
		if (!cp.connectToServer(listeners)) {
			System.err.println("Failed to connect to server!");
			System.exit(0);
		}

		final var homeDir = FileSystemView.getFileSystemView().getHomeDirectory().toPath();
		final var filePath = "C:\\Users\\djumi\\Desktop\\test.json";
		try {
			final var job = JobDescriptor.parse(new File(filePath));
			final var temp = Files.createTempDirectory(homeDir, "linda_job-");
			final var results = JobDescriptorIOOperations.createTempZip(job, temp);
			final var uploader = cp.submitJob(results.getZip(), new UploadingListener() {
				long bytesUploaded = 0;
				long totalSize = 0;
				long failCount = 0;
				{
					try {
						totalSize = Files.size(results.getZip().toPath());
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
						if (Files.walk(results.getDirectory()).sorted(Comparator.reverseOrder())
								.map(Path::toFile).map(File::delete).allMatch(b -> b)) {
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
						if (Files.walk(results.getDirectory()).sorted(Comparator.reverseOrder())
								.map(Path::toFile).map(File::delete).allMatch(b -> b)) {
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
		} catch (IOException | JsonSyntaxException | JsonIOException | JobCreationException e) {
			System.out.println("Failed to do stuff");
			e.printStackTrace();
		}
	}

	public void shutdown() {
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

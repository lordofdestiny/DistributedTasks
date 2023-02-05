package rs.ac.bg.etf.kdp.apps;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.client.ClientProcess;
import rs.ac.bg.etf.kdp.gui.client.ClientAppFrame;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.JobDescriptorIOOperations.TemporaryFiles;
import rs.ac.bg.etf.kdp.utils.JobDescriptorIOOperations;

public class ClientApp {
	static {
		Configuration.load();
	}

	private ClientAppFrame frame = new ClientAppFrame(UUID::randomUUID, () -> this.clientUUID);
	private UUID clientUUID = null;
	private ClientProcess process = null;

	{
		frame.setUUIDReadyListener((uuid) -> {
			clientUUID = uuid;
		});
		frame.setConnectInfoReadyListener((info) -> {
			process = new ClientProcess(clientUUID, info);
		});
		frame.setConnectListener(() -> {
			final var listeners = new ArrayList<ConnectionListener>(2);
			listeners.add(frame.getConnectionListener());
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

			if (process.connectToServer(listeners)) {
				System.out.println("Connected!");
			} else {
				System.err.println("Failed to connect to server!");
				System.exit(0);
			}
		});
		frame.setJobDescriptorListener((job) -> {
			try {
				final var prefix = "linda_job-";

				final var homeDir = FileSystemView.getFileSystemView().getHomeDirectory().toPath();
				final var temp = frame.tempOnDesktop() ? Files.createTempDirectory(homeDir, prefix)
						: Files.createTempDirectory(prefix);

				TemporaryFiles results = JobDescriptorIOOperations.createTempZip(job, temp);

				process.submitJob(results.getZip(), new UploadingListener() {
					long bytesUploaded = 0;
					long totalSize = 0;
					{
						try {
							totalSize = Files.size(results.getZip().toPath());
						} catch (IOException ignore) {
						}
						frame.setFileSizeText(String.format("%.2fKB", totalSize / 1024.0));
					}

					@Override
					public void onBytesUploaded(int bytes) {
						bytesUploaded += bytes;
						frame.setTransferedSize(String.format("%dB", bytesUploaded));
						if (totalSize != 0) {
							frame.setProgressBar((int) (bytesUploaded * 100.0 / totalSize));
						}
					}

					@Override
					public void onDeadlineExceeded() {
						frame.promptTransferFailed("Time limit exceeded! Check your connection");
						try {
							Files.walk(results.getDirectory()).sorted(Comparator.reverseOrder())
									.map(Path::toFile).forEach(File::delete);
						} catch (IOException e) {
							System.err.println("Failed to cleanup after failed job transfer");
						}
					}

					@Override
					public void onIOException() {
						frame.promptTransferFailed(
								"Files could not be read from disk. Try saving them on desktop!");
					}

					@Override
					public void onUploadComplete(long bytes) {
						try {
							Files.walk(results.getDirectory()).sorted(Comparator.reverseOrder())
									.map(Path::toFile).forEach(File::delete);
						} catch (IOException e) {
							System.err.println("Failed to cleanup after failed job transfer");
						}
						frame.promptTransferCompleteSucessfully();
					}

					@Override
					public void onFailedConnection() {
						frame.promptTransferFailed("Server is not available! Try later!");
					}
				});
			} catch (IOException e) {
				frame.showErrorToClient("Error",
						"Failed during creation of temporary files. Try setting temporary directory.");
			}
		});
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				ClientApp app = new ClientApp();
				app.frame.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}

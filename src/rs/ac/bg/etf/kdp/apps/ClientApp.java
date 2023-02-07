package rs.ac.bg.etf.kdp.apps;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;

import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.core.IServerClient.UnregisteredClientException;
import rs.ac.bg.etf.kdp.core.IServerClient.MultipleJobsException;
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
			listeners.add(new ConnectionListener() {
				@Override
				public void onConnected() {
					System.out.println("Connected!");
					frame.setConnected();
				}

				@Override
				public void onPingComplete(long ping) {
					System.out.printf("Ping: %d ms\n", ping);
					frame.updatePing(ping);
				}

				@Override
				public void onConnectionLost() {
					System.err.println("Connection lost!");
					frame.setConnectionLost();
				}

				@Override
				public void onReconnecting() {
					System.err.println("Reconnecting...");
					frame.setReconnecting();
				}

				@Override
				public void onReconnected(long ping) {
					System.out.printf("Reconnected, ping is %d ms\n", ping);
					frame.setReconnected(ping);
				}

				@Override
				public void onReconnectionFailed() {
					System.err.println("Reconnection failed!");
					frame.setReconnectionFailed();
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
			if (process == null) {
				frame.showErrorToClient("Not authenticated",
						"You need to authenticate in order to submit a job");
				return;
			}
			try {
				final var prefix = "linda_job-";

				final var homeDir = FileSystemView.getFileSystemView().getHomeDirectory().toPath();
				final var temp = frame.tempOnDesktop() ? Files.createTempDirectory(homeDir, prefix)
						: Files.createTempDirectory(prefix);

				TemporaryFiles results = JobDescriptorIOOperations.createTempZip(job, temp);
				process.submitJob(results.getZip(), new DefaultUploadListener(results));

			} catch (IOException e) {
				frame.showErrorToClient("Error",
						"Failed during creation of temporary files. Try setting temporary directory.");
			} catch (UnregisteredClientException e) {
				throw new RuntimeException(e);
			} catch (MultipleJobsException e) {
				throw new RuntimeException(e);
			}
		});
	}
	private class DefaultUploadListener implements UploadingListener{
		private long bytesUploaded = 0;
		private long totalSize = 0;
		private long failCount = 0;

		TemporaryFiles tmp;

		DefaultUploadListener(TemporaryFiles tmp){
			this.tmp = tmp;
		}

		{
			try {
				totalSize = Files.size(tmp.getZip().toPath());
			} catch (IOException ignore) {
			}
			frame.setFileSizeText(String.format("%.2fKB", totalSize / 1024.0));
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
			frame.setTransferedSize(String.format("%dB", bytesUploaded));
			if (totalSize != 0) {
				frame.setProgressBar((int) (bytesUploaded * 100.0 / totalSize));
			}
		}

		@Override
		public void onDeadlineExceeded() {
			frame.promptTransferFailed("Time limit exceeded! Check your connection");
			defaultCleanup(tmp.getDirectory());
		}

		@Override
		public void onIOException() {
			frame.promptTransferFailed(
					"Files could not be read from disk. Try saving them on desktop!");
		}

		@Override
		public void onUploadComplete(long bytes) {
			defaultCleanup(tmp.getDirectory());
			frame.promptTransferCompleteSucessfully();
		}

		@Override
		public void onFailedConnection() {
			frame.promptTransferFailed("Server is not available! Try later!");
		}
	}

	private static void defaultCleanup(Path dir) {
		cleanup(dir, () -> {
			System.out.printf("Cleand up the directory: %s\n", dir);
		}, (e) -> {
			System.err.println("Failed to cleanup after failed job transfer");
		});
	}

	private static void cleanup(Path dir, Runnable success, Consumer<IOException> failed) {
		try {
			if (Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile)
					.map(File::delete).allMatch(b -> b)) {
				success.run();
			} else {
				if (failed != null) {
					failed.accept(null);
				}
			}
		} catch (IOException e) {
			if (failed != null) {
				failed.accept(e);
			}
		}
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

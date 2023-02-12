package rs.ac.bg.etf.kdp.core.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.filechooser.FileSystemView;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import rs.ac.bg.etf.kdp.core.IServerClient.MultipleJobsException;
import rs.ac.bg.etf.kdp.core.IServerClient.UnregisteredClientException;
import rs.ac.bg.etf.kdp.core.client.ClientProcess.JobResultsTransferListener;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;
import rs.ac.bg.etf.kdp.utils.JobDescriptor.JobCreationException;

public class ClientProcessTest {
	private final static Path JOB_RESULTS_DIRECTORY = FileSystemView.getFileSystemView()
			.getHomeDirectory().toPath().resolve("Results");

	public static void main(String[] args) {
		final var cinfo = new ConnectionInfo("localhost", Configuration.SERVER_PORT);
		final var uuid = UUID.randomUUID();
		System.out.println(uuid);
		ClientProcess cp = new ClientProcess(uuid, cinfo, JOB_RESULTS_DIRECTORY);
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
				System.out.println(String.format("Ping: %d ms", ping));
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
				System.out.println(String.format("Reconnected, ping is %d ms", ping));
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

		cp.setFailedToStartListener((cause) -> {
			System.err.println(String.format("Job failed to start:\n %s", cause));
		});

		cp.setJobFailedListener((JobTreeNode) -> {
			System.err.println(String.format("Job %s failed!", JobTreeNode.jobUUID));
		});

		cp.setJobResultsTransferListener(new JobResultsTransferListener() {

			@Override
			public void onResultsReceived(Path location) {
				try {
					final var allResults = location.getParent();
					FileOperations.unzip(location.toFile(), allResults);
					Files.delete(location);
					FileOperations.unzipAllInTree(allResults, (path) -> path.getParent(), true);
					System.out.println(String.format("You can find job results at:\n %s",
							location.toString()));
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Failed to unzip results");
				}
			}

			@Override
			public void onTransferFailed() {
				System.out.println("Failed to receive job results from the client");
			}
		});

		final var homeDir = FileSystemView.getFileSystemView().getHomeDirectory().toPath();
		final var filePath = homeDir.resolve("Lindica.json").toFile();
		Path tempDir = null;
		try {
			final var job = JobDescriptor.parse(filePath);
			tempDir = Files.createTempDirectory(homeDir, "linda_job-");
			final var copies = job.copyFilesToDirectory(tempDir);
			final var zipFile = tempDir.resolve("job.zip").toFile();
			FileOperations.zipFileList(copies, zipFile);

			final var temp = tempDir;
			final var uploader = cp.submitJob(zipFile, new UploadingListener() {
				long bytesUploaded = 0;
				long totalSize = 0;
				{
					try {
						totalSize = Files.size(zipFile.toPath());
					} catch (IOException ignore) {
					}
					System.out.println(String.format("Upload size: %.2fKB", totalSize / 1024.0));
				}

				@Override
				public void onBytesUploaded(int bytes) {
					bytesUploaded += bytes;
					System.out.println(String.format("Transfered %dB so far.", bytesUploaded));
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
		} catch (IOException | RemoteIOException | JsonSyntaxException | JsonIOException
				| JobCreationException | UnregisteredClientException | MultipleJobsException e) {
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

}

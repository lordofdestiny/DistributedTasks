package rs.ac.bg.etf.kdp.apps.client;

import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.IClientServer.JobTreeNode;
import rs.ac.bg.etf.kdp.core.IServerClient.MultipleJobsException;
import rs.ac.bg.etf.kdp.core.IServerClient.ResultRequestCode;
import rs.ac.bg.etf.kdp.core.IServerClient.UnregisteredClientException;
import rs.ac.bg.etf.kdp.core.client.ClientProcess;
import rs.ac.bg.etf.kdp.core.client.ClientProcess.JobResultsTransferListener;
import rs.ac.bg.etf.kdp.gui.client.ClientAppFrame;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;

public class ClientApp {
	static {
		Configuration.load();
	}

	private Path JOB_RESULTS_DIRECTORY = FileSystemView.getFileSystemView().getHomeDirectory()
			.toPath().resolve("Results");

	private ArrayList<JobTreeNode> failedJobs = new ArrayList<>();

	private final ReentrantLock singleDialogLock = new ReentrantLock();
	private ClientAppFrame frame = new ClientAppFrame(singleDialogLock, UUID::randomUUID,
			() -> this.clientUUID);
	private UUID clientUUID = null;
	private ClientProcess process = null;

	public ClientApp() {
		frame.setUUIDReadyListener((uuid) -> {
			clientUUID = uuid;
		});
		frame.setConnectInfoReadyListener((info) -> {
			process = new ClientProcess(clientUUID, info, JOB_RESULTS_DIRECTORY);

			process.setFailedToStartListener((cause) -> {
				System.err.println(String.format("Job failed to start:\n %s", cause));
			});

			frame.setIndexCallback((i) -> {
				if (i < 0 || i >= failedJobs.size()) {
					return null;
				}
				return failedJobs.get(i);
			});

			process.setJobFailedListener((jobTreeNode) -> {
				frame.incrementFailureCount();
				failedJobs.add(jobTreeNode);
				frame.acceptTree(jobTreeNode, failedJobs.size() - 1);
			});

			process.setJobResultsTransferListener(new JobResultsTransferListener() {

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
						System.err.println("Failed to unzip results");
					}
					frame.showNotification("Results received", String
							.format("You can find job results at:\n %s", location.toString()));

				}

				@Override
				public void onTransferFailed() {
					frame.showErrorToClient("Results not received",
							"Failed to receive job results from the client");
				}
			});
		});
		frame.setUserConnectListener(this::userConnectHandler);

		frame.setJobDescriptorListener(this::uploadNewJob);

		frame.setFetchResultsListener(() -> {
			if (process == null) {
				return;
			}
			if (process.requestResults() == ResultRequestCode.UNKNOWN) {
				frame.showNotification("Job state unknown",
						"Server has no information on job data for this client");
			}
		});

		frame.setFailedJobActionListener((p) -> {
			process.respondToJobFailure(p);
			failedJobs.clear();
		});

		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				singleDialogLock.lock();
				int dialogResult = JOptionPane.showConfirmDialog(frame,
						"Are you sure you want to exit?", "Exit", JOptionPane.YES_NO_OPTION);
				singleDialogLock.unlock();
				if (dialogResult == JOptionPane.YES_OPTION) {
					if (process != null) {
						process.shutdown();
					}
					frame.dispose();
					System.exit(0);
				}
			}
		});
	}

	private void userConnectHandler() {
		if (process.connected()) {
			return;
		}
		final var listener = new ClientAppConenctionListener(frame);
		if (!process.connectToServer(listener)) {
			System.err.println("Failed to connect to server!");
		}
	}

	private void uploadNewJob(JobDescriptor job) {
		if (process == null) {
			frame.showErrorToClient("Not authenticated",
					"You need to authenticate in order to submit a job");
			return;
		}
		if (job == null) {
			frame.showErrorToClient("Unexpected error", "Please try to load the job again.");
			return;
		}
		Path tempDir = null;
		try {
			final var prefix = "linda_job-";

			final var homeDir = FileSystemView.getFileSystemView().getHomeDirectory().toPath();
			tempDir = frame.tempOnDesktop() ? Files.createTempDirectory(homeDir, prefix)
					: Files.createTempDirectory(prefix);

			final var zipFile = tempDir.resolve("job.zip").toFile();
			final var copies = job.copyFilesToDirectory(tempDir);
			FileOperations.zipFileList(copies, zipFile);

			long totalSize = 0;
			try {
				totalSize = Files.size(zipFile.toPath());
				frame.setFileSizeText(String.format("%.2fKB", totalSize / 1024.0));
			} catch (IOException ignore) {
			}

			process.submitJob(zipFile,
					new JobUploadListener(totalSize, tempDir, frame, singleDialogLock));

		} catch (IOException e) {
			frame.showErrorToClient("Error",
					"Failed during creation of temporary files. Try setting temporary directory.");
		} catch (UnregisteredClientException e) {
			frame.showErrorToClient("Registration error", "Your did not register.");
		} catch (MultipleJobsException e) {
			frame.showErrorToClient("Submission error",
					"You can't queue another job before " + "the first one is complete");
		} catch (RemoteIOException e) {
			frame.showErrorToClient("Submission error", "Internal server filesystem error");
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

package rs.ac.bg.etf.kdp.apps.client;

import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.IServerClient.MultipleJobsException;
import rs.ac.bg.etf.kdp.core.IServerClient.UnregisteredClientException;
import rs.ac.bg.etf.kdp.core.client.ClientProcess;
import rs.ac.bg.etf.kdp.gui.client.ClientAppFrame;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class ClientApp {
	static {
		Configuration.load();
	}

	private ClientAppFrame frame = new ClientAppFrame(UUID::randomUUID, () -> this.clientUUID);
	private UUID clientUUID = null;
	private ClientProcess process = null;

	public ClientApp() {
		frame.setUUIDReadyListener((uuid) -> {
			clientUUID = uuid;
		});
		frame.setConnectInfoReadyListener((info) -> {
			process = new ClientProcess(clientUUID, info);
		});
		frame.setUserConnectListener(() -> {
			if (process.connected()) {
				return;
			}
			final var listener = new ClientAppConenctionListener(frame);
			if (process.connectToServer(listener)) {
				System.out.println("Connected!");
			} else {
				System.err.println("Failed to connect to server!");
				System.exit(0);
			}
		});
		frame.setJobDescriptorListener(this::uploadNewJob);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				int dialogResult = JOptionPane.showConfirmDialog(frame,
						"Are you sure you want to exit?", "Exit", JOptionPane.YES_NO_OPTION);
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
			FileOperations.zip(copies, zipFile);

			long totalSize = 0;
			try {
				totalSize = Files.size(zipFile.toPath());
				frame.setFileSizeText(String.format("%.2fKB", totalSize / 1024.0));
			} catch (IOException ignore) {
			}

			process.submitJob(zipFile, new JobUploadListener(totalSize, tempDir, frame));

		} catch (IOException e) {
			frame.showErrorToClient("Error",
					"Failed during creation of temporary files. Try setting temporary directory.");
		} catch (UnregisteredClientException e) {
			frame.showErrorToClient("Registration error", "Your did not register.");
		} catch (MultipleJobsException e) {
			frame.showErrorToClient("Submission error",
					"You can't queue another job before " + "the first one is complete");
		}catch(RemoteIOException e) {
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

package rs.ac.bg.etf.kdp.apps;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.ClientProcess;
import rs.ac.bg.etf.kdp.gui.client.ClientAppFrame;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.JobRequestDescriptor;

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
			}else {
				System.err.println("Failed to connect to server!");
				System.exit(0);
			}
		});
		frame.setJobDescriptorListener((job) -> {
			try {
				Path[] results = createTempZipFromDescriptor(job);
				// create transfer listener
				process.sendJob(results[0], null);
			} catch (IOException e) {
				frame.showErrorToClient("Error",
						"Failed during creation of temporary files. Try setting temporary directory.");
			}
		});
	}

	private void deleteTempDirectory(Path dir) throws IOException {
		for (final var file : dir.toFile().listFiles()) {
			file.delete();
		}
		Files.delete(dir);
	}

	private Path[] createTempZipFromDescriptor(JobRequestDescriptor jrd) throws IOException {
		final var prefix = "linda_job-";

		final var temp = frame.tempOnDesktop()
				? Files.createTempDirectory(FileSystemView.getFileSystemView().getHomeDirectory().toPath(), prefix)
				: Files.createTempDirectory(prefix);

		System.out.println(temp.toString());// debugging

		final var copyPaths = JobRequestDescriptor.copyFilesToPath(temp, jrd);

		final var zipFile = new File(temp.toFile(), "job.zip");

		try (final var fos = new FileOutputStream(zipFile); final var zipOut = new ZipOutputStream(fos)) {
			for (final var path : copyPaths) {
				final var file = path.toFile();
				try (final var fis = new FileInputStream(file)) {
					final var entry = new ZipEntry(file.getName());
					zipOut.putNextEntry(entry);

					final var bytes = new byte[1024];
					int length;
					while ((length = fis.read(bytes)) >= 0) {
						zipOut.write(bytes, 0, length);
					}
				}
			}
		} catch (IOException e) {
			deleteTempDirectory(temp);
		}
		return new Path[] { zipFile.toPath(), temp };
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

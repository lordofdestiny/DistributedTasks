package rs.ac.bg.etf.kdp.apps.client;

import java.io.IOException;
import java.nio.file.Path;

import rs.ac.bg.etf.kdp.gui.client.ClientAppFrame;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;

class JobUploadListener implements UploadingListener {
	private long bytesUploaded = 0;
	private long totalSize = 0;
	private long failCount = 0;
	private Path temp;
	private ClientAppFrame frame;

	JobUploadListener(long fileSize, Path dir, ClientAppFrame frame) {
		this.temp = dir;
		this.frame = frame;
		this.totalSize = fileSize;
	}

	private static void cleanup(Path dir) {
		try {
			if (FileOperations.deleteDirectory(dir)) {
				System.out.printf("Cleand up the directory: %s\n", dir);
			} else {
				System.err.println("Failed to cleanup after failed job transfer");

			}
		} catch (IOException e) {
			System.err.println("Failed to cleanup after failed job transfer");

		}
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
		cleanup(temp);
	}

	@Override
	public void onIOException() {
		frame.promptTransferFailed(
				"Files could not be read from disk. Try saving them on desktop!");
	}

	@Override
	public void onUploadComplete(long bytes) {
		cleanup(temp);
		frame.promptTransferCompleteSucessfully();
	}

	@Override
	public void onFailedConnection() {
		frame.promptTransferFailed("Server is not available! Try later!");
	}
}
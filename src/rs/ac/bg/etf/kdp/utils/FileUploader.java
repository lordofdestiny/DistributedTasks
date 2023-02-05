package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

import rs.ac.bg.etf.kdp.utils.IFileDownloader.DeadlineExceededException;

public class FileUploader extends Thread {
	public interface UploadingListener {
		void onFailedConnection();
		
		void onBytesUploaded(int bytes);

		void onDeadlineExceeded();

		void onIOException();

		void onUploadComplete(long bytes);
	}

	private final File zipFile;
	private final UploadingListener listener;
	private FileUploadHandle handle;

	public FileUploader(FileUploadHandle ftd, File zip, UploadingListener cb) {
		Objects.requireNonNull(ftd);
		Objects.requireNonNull(zip);
		zipFile = zip;
		listener = cb;
		this.handle = ftd;
	}

	@Override
	public void run() {
		long bytesTransfered = 0;

		// Add listener hooks
		try (var fis = new FileInputStream(zipFile)) {
			int bytesRead;
			byte[] buffer = new byte[Configuration.MAX_BUFFER_SIZE];
			while ((bytesRead = fis.read(buffer)) != -1) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				while (!handle.uploadBytes(buffer, bytesRead)) {
				}
				listener.onBytesUploaded(bytesRead);
				bytesTransfered += bytesRead;
			}

			while (!handle.confirmTransfer()) {
			}
			final var result = bytesTransfered;
			listener.onUploadComplete(result);
		} catch (IOException e) {
			listener.onIOException();
			return;
		} catch (DeadlineExceededException e) {
			listener.onDeadlineExceeded();
		}
	}
}
package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

import rs.ac.bg.etf.kdp.utils.IFileDownloader.DeadlineExceededException;

public class FileUploader extends Thread {
	public interface UploadingListener {
		default void onFailedConnection() {

		}

		default void onBlockUploadFailed(int blockNo) {

		}

		default void onBytesUploaded(int bytes) {

		}

		default void onDeadlineExceeded() {

		}

		default void onIOException() {

		}

		default void onUploadComplete(long bytes) {

		}
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
			int blockNo = 0;
			byte[] buffer = new byte[Configuration.MAX_BUFFER_SIZE];
			while ((bytesRead = fis.read(buffer)) != -1) {

				while (!handle.uploadBytes(buffer, bytesRead)) {
					listener.onBlockUploadFailed(blockNo);
				}
				listener.onBytesUploaded(bytesRead);
				bytesTransfered += bytesRead;
			}

			while (!handle.confirmTransfer()) {
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
			listener.onIOException();
			return;
		} catch (DeadlineExceededException e) {
			e.printStackTrace();
			listener.onDeadlineExceeded();
			return;
		}
		listener.onUploadComplete(bytesTransfered);
	}
}
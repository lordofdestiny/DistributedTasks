package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.time.Instant;
import java.time.temporal.TemporalUnit;

public interface IFileDownloader extends Remote, Serializable {
	public static class DownloadingToken {
		private Instant deadline;
		private File location;

		public DownloadingToken(File file, long duration, TemporalUnit unit) {
			this.deadline = Instant.now().plus(duration, unit);
			this.location = file;
		}

		public Instant deadline() {
			return deadline;
		}

		public boolean deadlineExceeded() {
			return Instant.now().isAfter(deadline);
		}

		public File getFileLocation() {
			return location;
		}
	}

	public interface DownloadingListener {
		default void onBytesReceived(int bytes) {

		}

		void onTransferComplete();

		void onDeadlineExceeded();
	}

	static class FileTransferException extends Exception {
		private static final long serialVersionUID = 1L;

		public FileTransferException() {
		}

		public FileTransferException(String msg) {
			super(msg);
		}

		public FileTransferException(Throwable cause) {
			super(cause);
		}

		public FileTransferException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}

	static class RemoteIOException extends FileTransferException {
		private static final long serialVersionUID = 1L;

		public RemoteIOException(IOException cause) {
			super("Error while saving transfered bytes", cause);
		}
	}

	static class DeadlineExceededException extends FileTransferException {
		private static final long serialVersionUID = 1L;

		public DeadlineExceededException() {
			super("Transfer operation performed after deadline was exceeded");
		}
	}

	void receiveBytes(byte[] bytes, int bytesRead)
			throws RemoteException, RemoteIOException, DeadlineExceededException;

	void confirmTransfer() throws RemoteException, DeadlineExceededException;
}

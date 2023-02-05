package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IFileDownloader extends Remote, Serializable {
	public interface IDownloadable {
		boolean deadlineExpired();
		File getFileLocation();
	}

	public interface DownloadingListener {
		void onBytesReceived(int bytes);

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

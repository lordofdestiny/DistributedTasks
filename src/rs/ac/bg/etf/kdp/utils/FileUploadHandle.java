package rs.ac.bg.etf.kdp.utils;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.time.Instant;

import rs.ac.bg.etf.kdp.utils.IFileDownloader.DeadlineExceededException;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class FileUploadHandle implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private IFileDownloader receiver = null;
	private Instant deadline = null;
	private boolean valid = false;

	public FileUploadHandle() {
	}

	public FileUploadHandle(IFileDownloader receiver, Instant deadline) {
		this.receiver = receiver;
		this.deadline = deadline;
		valid = true;
	}

	public boolean uploadBytes(byte[] bytes, int bytesRead) throws DeadlineExceededException {
		try {
			if (Instant.now().isAfter(deadline)) {
				throw new DeadlineExceededException();
			}
			receiver.receiveBytes(bytes, bytesRead);
			return true;
		} catch (RemoteException | RemoteIOException e) {
			return false;
		}
	}

	public boolean confirmTransfer() throws DeadlineExceededException {
		try {
			if (Instant.now().isAfter(deadline)) {
				throw new DeadlineExceededException();
			}
			receiver.confirmTransfer();
			return true;
		} catch (RemoteException e) {
			return false;
		}
	}
	
	public boolean isValid() {
		return valid;
	}
}

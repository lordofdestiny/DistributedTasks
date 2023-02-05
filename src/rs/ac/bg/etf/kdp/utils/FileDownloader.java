package rs.ac.bg.etf.kdp.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.time.temporal.TemporalAmount;

public class FileDownloader extends UnicastRemoteObject implements IFileDownloader, Unreferenced {
	private static final long serialVersionUID = 1L;
	private IDownloadable record;
	private DownloadingListener listener;

	public FileDownloader(IDownloadable record, DownloadingListener listener,
			TemporalAmount deadlineExtension) throws RemoteException {
		super();
		this.record = record;
		this.listener = listener;
	}

	public void receiveBytes(byte[] bytes, int bytesRead)
			throws RemoteException, RemoteIOException, DeadlineExceededException {
		// TimeLimited inteface implementing deadlineExpired and
		if (record.deadlineExpired()) {
			listener.onDeadlineExceeded();
			throw new DeadlineExceededException();
		}
		try (final var fos = new FileOutputStream(record.getFileLocation(), true)) {
			fos.write(bytes, 0, bytesRead);
		} catch (IOException e) {
			throw new RemoteIOException(e);
		}
		listener.onBytesReceived(bytesRead);
	}

	@Override
	public void confirmTransfer() throws RemoteException, DeadlineExceededException {
		if (record.deadlineExpired()) {
			listener.onDeadlineExceeded();
			throw new DeadlineExceededException();
		}
		listener.onTransferComplete();
	}

	@Override
	public void unreferenced() {
		try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
			e.printStackTrace();
		}
	}

}

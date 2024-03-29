package rs.ac.bg.etf.kdp.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class FileDownloader extends UnicastRemoteObject implements IFileDownloader, Unreferenced {
	private static final long serialVersionUID = 1L;

	private static final ScheduledExecutorService deadlineSignalingPool = Executors
			.newScheduledThreadPool(1);
	private static final ExecutorService deadlineHandlingPool = Executors.newCachedThreadPool();

	private DownloadingToken token;
	private DownloadingListener listener;
	private boolean complete = false;
	private boolean deadlineExceeded = false;
	private ReentrantLock lock = new ReentrantLock();

	public FileDownloader(DownloadingToken token, DownloadingListener listener)
			throws RemoteException {
		this.token = token;
		this.listener = listener;
		try {
			Files.createDirectories(token.getFileLocation().toPath().getParent());
		} catch (IOException e) {

		}
		final var delay = Duration.between(Instant.now(), token.deadline());
		deadlineSignalingPool.schedule(() -> {
			final boolean locked = lock.tryLock();
			deadlineHandlingPool.submit(() -> {
				try {
					if (!locked) {
						lock.lock();
					}
					if (complete) {
						return;
					}
					deadlineExceeded = true;
				} finally {
					lock.unlock();
				}
				listener.onDeadlineExceeded();
			});

		}, delay.toSeconds(), TimeUnit.SECONDS);
	}

	public void receiveBytes(byte[] bytes, int bytesRead)
			throws RemoteException, RemoteIOException, DeadlineExceededException {
		lock.lock();
		try {
			if (token.deadlineExceeded() || deadlineExceeded) {
				throw new DeadlineExceededException();
			}
		} finally {
			lock.unlock();
		}
		try (final var fos = new FileOutputStream(token.getFileLocation(), true)) {
			fos.write(bytes, 0, bytesRead);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RemoteIOException(e);
		}
		listener.onBytesReceived(bytesRead);
	}

	@Override
	public void confirmTransfer() throws RemoteException, DeadlineExceededException {
		lock.lock();
		try {
			if (token.deadlineExceeded() || deadlineExceeded) {
				throw new DeadlineExceededException();
			}
			complete = true;
		} finally {
			lock.unlock();
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

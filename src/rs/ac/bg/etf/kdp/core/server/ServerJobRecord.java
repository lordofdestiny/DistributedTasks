package rs.ac.bg.etf.kdp.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.JobStatus;

abstract class ServerJobRecord {
	protected UUID userUUID;
	protected UUID mainJobUUID;
	protected UUID parentJobUUID;
	protected UUID jobUUID;
	protected Path mainJobDirectory;
	protected Path jobDirectory; // TODO CHANGE THE NAME
	protected File zipFile;
	protected JobStatus status;
	protected List<ServerJobRecord> children = new CopyOnWriteArrayList<>();
	protected AtomicInteger failedToStartCount = new AtomicInteger();

	protected Lock resultFilesLock = new ReentrantLock();
	protected boolean resultGenerated = false;

	ServerJobRecord(UUID userUUID, UUID mainJobUUID, UUID parentJobUUID, UUID jobUUID,
			Path directory) throws IOException {
		this.jobUUID = jobUUID;
		this.userUUID = userUUID;
		this.mainJobUUID = mainJobUUID;
		this.parentJobUUID = parentJobUUID;
		this.jobDirectory = directory;
		this.mainJobDirectory = getPath(userUUID, mainJobUUID);
		Files.createDirectories(jobDirectory);
		this.zipFile = mainJobDirectory.resolve("job.zip").toFile();
		this.status = JobStatus.REGISTERED;
	}

	// General

	protected UUID getClientUUID() {
		return userUUID;
	}

	protected UUID getMainJobUUID() {
		return mainJobUUID;
	}

	protected UUID getJobUUID() {
		return jobUUID;
	}

	protected Path getMainJobDirectory() {
		return mainJobDirectory;
	}

	protected Path getJobDirectory() {
		return jobDirectory;
	}

	protected synchronized JobStatus getStatus() {
		return status;
	}

	protected synchronized void setStatus(JobStatus status) {
		this.status = status;
	}

	protected File getZipFile() {
		return zipFile;
	}

	protected abstract FileUploadHandle postToWorker(IWorkerServer worker)
			throws RemoteException, RemoteIOException;

	private static Path getPath(UUID user, UUID job) {
		return ServerProcess.CHANGE_LATER_ROOT_DIR.toAbsolutePath().resolve(user.toString())
				.resolve(job.toString());
	}

	protected abstract String description();

	protected synchronized boolean isFullyComplete() {
		if (status != JobStatus.DONE) {
			return false;
		}

		final var stack = new ArrayDeque<ServerJobRecord>();
		stack.push(this);

		while (!stack.isEmpty()) {
			final var current = stack.pop();

			final var reversed = current.children.listIterator(current.children.size());
			while (reversed.hasPrevious()) {
				final var child = reversed.previous();
				if (child.status != JobStatus.DONE) {
					return false;
				}
				stack.push(child);
			}
		}
		return true;
	}

	protected int getFailedToStartCount() {
		return failedToStartCount.get();
	}

	protected int failedToStartCountUp() {
		return failedToStartCount.incrementAndGet();
	}

	protected Lock getResultFilesLock() {
		return resultFilesLock;
	}

	protected void setResultFilesGenerated() {
		resultGenerated = true;
	}

	protected boolean resultFilesGenerated() {
		return resultGenerated;
	}
}

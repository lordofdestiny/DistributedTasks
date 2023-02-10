package rs.ac.bg.etf.kdp.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

abstract class ServerJobRecord {
	protected UUID userUUID;
	protected UUID mainJobUUID;
	protected UUID jobUUID;
	protected Path rootDir;
	protected File zipFile;
	protected JobStatus status;

	ServerJobRecord(UUID userUUID, UUID mainJobUUID, UUID jobUUID) throws IOException {
		this.jobUUID = jobUUID;
		this.userUUID = userUUID;
		this.mainJobUUID = mainJobUUID;
		rootDir = getPath(userUUID, jobUUID);
		Files.createDirectories(rootDir);
		zipFile = rootDir.resolve("job.zip").toFile();
		status = JobStatus.REGISTERED;
	}

	// General

	protected UUID getUserUUID() {
		return userUUID;
	}

	protected UUID getMainJobUUID() {
		return mainJobUUID;
	}

	protected UUID getJobUUID() {
		return jobUUID;
	}

	protected Path getRootDir() {
		return rootDir;
	}

	protected synchronized JobStatus getStatus() {
		return status;
	}

	protected synchronized void setStatus(JobStatus status) {
		this.status = status;
	}

	protected File getFileLocation() {
		return zipFile;
	}

	protected abstract FileUploadHandle postToWorker(IWorkerServer worker)
			throws RemoteException, RemoteIOException;

	// READY
	// Do I need anything here ?

	// SCHEDULED
	// Worker ? Time ?

	// RUNNING
	// DONE
	// FAILED
	// ABORTED

	static enum JobStatus {
		REGISTERED, READY, SCHEDULED, RUNNING, DONE, FAILED, ABORTED;
	}

	private static Path getPath(UUID user, UUID job) {
		return ServerProcess.CHANGE_LATER_ROOT_DIR.toAbsolutePath().resolve(user.toString())
				.resolve(job.toString());
	}
}

package rs.ac.bg.etf.kdp.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.IFileDownloader.IDownloadable;

class JobRecord implements IDownloadable {
	UUID userUUID;
	UUID jobUUID;
	Path rootDir;
	File zipFile;
	JobStatus status;

	JobRecord(UUID userUUID, UUID jobUUID, Instant deadline) {
		this.jobUUID = jobUUID;
		this.userUUID = userUUID;
		this.deadline = deadline;
		rootDir = getPath(userUUID, jobUUID);
		try {
			Files.createDirectories(rootDir);
		} catch (IOException e) {
			// Throw can't create directory, hard fail
			e.printStackTrace();
		}
		zipFile = getZipPath(rootDir);
		status = JobStatus.REGISTERED;
	}

	// General
	UUID userUUID() {
		return userUUID;
	}

	UUID getUUID() {
		return jobUUID;
	}
	
	Path getRootDir() {
		return rootDir;
	}

	synchronized JobStatus getStatus() {
		return status;
	}

	synchronized void setStatus(JobStatus status) {
		this.status = status;
	}

	// REGISTERED
	private Instant deadline;

	synchronized void setDeadline(Instant deadline) {
		this.deadline = deadline;
	}
	// IRecievable
	public synchronized boolean deadlineExpired() {
		return Instant.now().isAfter(deadline);
	}

	@Override
	public File getFileLocation() {
		return zipFile;
	}

	// READY
	// SCHEDULED
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

	private static File getZipPath(Path rootDir) {
		return rootDir.resolve("job.zip").toFile();
	}
}

package rs.ac.bg.etf.kdp.core.worker;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static rs.ac.bg.etf.kdp.core.worker.WorkerProcess.CHANGE_LATER_ROOT_DIR;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import rs.ac.bg.etf.kdp.utils.JobDescriptor;

public class WorkerJobRecord {
	private UUID userUUID;
	private UUID mainJobUUID;
	private UUID parentJobUUID;
	private UUID jobUUID;
	private Path mainJobDirectory;
	private Path jobDirectory;
	private Path resultsDir;
	private Path workingDir;
	private JobDescriptor job;
	private JobTask task;
	private AtomicInteger failedUploadCount = new AtomicInteger(0);

	public WorkerJobRecord(UUID userUUID, UUID mainJobUUID, UUID parentJobUUID, UUID jobUUID) {
		this.userUUID = userUUID;
		this.mainJobUUID = mainJobUUID;
		this.jobUUID = jobUUID;
		this.mainJobDirectory = CHANGE_LATER_ROOT_DIR.resolve(mainJobUUID.toString());
		this.jobDirectory = mainJobDirectory.resolve(jobUUID.toString());
		this.resultsDir = jobDirectory.resolve("results");
		this.workingDir = jobDirectory.resolve("workdir");
	}

	public File getZipLocation() {
		return mainJobDirectory.resolve("job.zip").toFile();
	}

	public WorkerJobRecord(UUID userUUID, UUID jobUUID) {
		this(userUUID, jobUUID, null, jobUUID);
	}

	public UUID getUserUUID() {
		return userUUID;
	}

	public UUID getMainJobUUID() {
		return mainJobUUID;
	}

	public UUID getParentJobUUID() {
		return parentJobUUID;
	}

	public UUID getJobUUID() {
		return jobUUID;
	}

	public Path getMainDirectory() {
		return mainJobDirectory;
	}

	public Path getJobDirectory() {
		return jobDirectory;
	}

	public void createDirectories() throws IOException {
		Files.createDirectories(mainJobDirectory);
		Files.createDirectories(jobDirectory);
		Files.createDirectories(resultsDir);
		Files.createDirectories(workingDir);
	}

	public Path getResultsDir() {
		return resultsDir;
	}

	public void setWorkDir(Path dir) {
		workingDir = dir;
	}

	public Path getWorkingDir() {
		return workingDir;
	}

	public void setJobDescriptor(JobDescriptor job) {
		this.job = job;
	}

	public JobDescriptor getJobDescriptor() {
		return job;
	}

	private static Predicate<Path> fileNameNotIn(Set<String> set) {
		return (file) -> !set.contains(file.toFile().getName());
	}

	private static Predicate<Path> isDirectory() {
		return (file) -> !file.toFile().isFile();
	}

	public void generateResults() throws IOException {
		final var names = new HashSet<>(List.of(job.getInFiles()));

		final var outFiles = Files.walk(workingDir).filter(fileNameNotIn(names))
				.filter(Predicate.not(isDirectory()));

		for (final var outFile : (Iterable<Path>) outFiles::iterator) {
			final var dest = resultsDir.resolve(workingDir.relativize(outFile));
			Files.copy(outFile, dest, REPLACE_EXISTING);
		}
	}

	public void setTask(JobTask task) throws IOException {
		this.task = task;
	}

	public JobTask getTask() {
		return task;
	}

	public int countUpUploadFailures() {
		return failedUploadCount.incrementAndGet();
	}

	public int getUploadFailureCount() {
		return failedUploadCount.get();
	}
}

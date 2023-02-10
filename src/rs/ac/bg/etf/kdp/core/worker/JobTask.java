package rs.ac.bg.etf.kdp.core.worker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import rs.ac.bg.etf.kdp.utils.Configuration;

public abstract class JobTask {
	protected File jarFile;
	protected File stdoutFile;
	protected File stderrFile;
	protected WorkerJobRecord record;
	protected final ProcessBuilder pb = new ProcessBuilder();
	protected Process process;
	
	public JobTask(WorkerJobRecord record) throws IOException{
		final var job = record.getJobDescriptor();
		this.jarFile = job.getManifestDir().resolve(job.getJAR()).toFile();
		this.record = record;
		stdoutFile = record.getResultsDir().resolve("stdout.txt").toFile();
		stderrFile = record.getResultsDir().resolve("stderr.txt").toFile();

		final var env = pb.environment();
		env.put("LINDA_REMOTE", "true");
		env.put("LINDA_HOST", "localhost");
		env.put("LINDA_PORT", "8080");
		env.put("LINDA_ROUTE", Configuration.SERVER_ROUTE);
		env.put("LINDA_USER", record.getUserUUID().toString());
		env.put("LINDA_MAIN_JOB", record.getMainJobUUID().toString());

		pb.command(buildCommand());
		pb.redirectOutput(stdoutFile);
		pb.redirectError(stderrFile);
		pb.directory(record.getWorkingDir().toFile());
		
		Files.createDirectories(record.getResultsDir());
		Files.createDirectories(record.getWorkingDir());
		
		stdoutFile.createNewFile();
		stderrFile.createNewFile();
		job.copyInputFiles(record.getWorkingDir());	
	}
	
	protected abstract List<String> buildCommand();
	
	final public Process getProcess() throws IOException {
		if (process == null) {
			process = pb.start();
		}
		return process;
	}
}

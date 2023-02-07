package rs.ac.bg.etf.kdp.utils;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

public class JobDescriptor {
	static class JobFiles {
		String[] in = {};
		String[] out = {};

		JobFiles() {

		}

		JobFiles(String[] inFiles, String[] outFiles) {
			this.in = inFiles;
			this.out = outFiles;
		}

		public String[] getIn() {
			return in;
		}

		public String[] getOut() {
			return out;
		}
	}

	@SerializedName("job")
	String jobJar;
	String name;
	String mainClass;
	String[] args;
	JobFiles files = new JobFiles();
	transient Path confFileDir;
	transient String confFileName;
	transient boolean fromConstructor = false;

	public static class JobCreationException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public JobCreationException(String message) {
			super(message);
		}
	}

	public JobDescriptor() {

	}

	public JobDescriptor(String name, File jobJar, String mainClass, String args,
			ArrayList<String> inFiles, ArrayList<String> outFiles) throws JobCreationException {
		if (!jobJar.exists() || !JarVerificator.isValidJar(jobJar)
				|| !JarVerificator.hasClass(jobJar, mainClass, false)) {
			throw new JobCreationException("Bad JAR file");
		}
		this.name = name;
		this.jobJar = jobJar.getName();
		this.mainClass = mainClass;
		this.args = args != null ? args.replaceAll("\\s+", " ").split(" ") : new String[0];
		this.files.in = inFiles != null
				? inFiles.stream().filter(String::isBlank).toArray(String[]::new)
				: new String[0];
		this.files.out = outFiles != null
				? outFiles.stream().filter(String::isBlank).toArray(String[]::new)
				: new String[0];
		this.fromConstructor = true;
	}

	public static JobDescriptor parse(File file) throws IOException, JobCreationException,
			JsonSyntaxException, JsonIOException {
		final var gson = new Gson();

		JobDescriptor jd;
		try(Reader reader = new FileReader(file)){
			jd = gson.fromJson(reader, JobDescriptor.class);
			if (jd == null) {
				throw new JobCreationException("Invalid file format");
			}
			jd.confFileDir = file.getParentFile().toPath();
			jd.confFileName = file.getName();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return jd;
	}

	public String getName() {
		return name;
	}

	public String getJAR() {
		return jobJar;
	}

	public String getMainClassName() {
		return mainClass;
	}

	public String[] getArgs() {
		return args;
	}

	public String[] getInFiles() {
		return files.in;
	}

	public String[] getOutFiles() {
		return files.out;
	}

	public static JobDescriptor resolveFileNames(JobDescriptor job) {
		if (job.fromConstructor) {
			return job;
		}
		final var desc = new JobDescriptor();
		desc.name = job.name;
		desc.confFileDir = job.confFileDir;
		desc.jobJar = job.confFileDir.resolve(job.jobJar).toAbsolutePath().toString();
		desc.confFileName = job.confFileDir.resolve(job.confFileName).toAbsolutePath().toString();
		desc.mainClass = job.mainClass;
		desc.args = job.args;

		final var jobInFiles = Stream.of(job.files.in).map(job.confFileDir::resolve)
				.map(Path::toAbsolutePath).map(Path::toString).toArray(String[]::new);

		desc.files = new JobFiles(jobInFiles, job.files.out);
		return desc;
	}
}

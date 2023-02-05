package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Stream;

public class JobDescriptorValidator {
	private JobDescriptor job;
	private File jarFile;

	public JobDescriptorValidator(JobDescriptor job) {
		this.job = job;
		this.jarFile = job.confFileDir.resolve(job.jobJar).toFile();
	}

	public boolean isValidFormat() {
		return job.jobJar != null && job.mainClass != null
				&& ((job.files == null) || job.files.in.length <= 6 && job.files.out.length <= 6);
	}

	public boolean hasValidFiles() {
		return Stream.of(job.files.in).map(job.confFileDir::resolve).map(Path::toFile)
				.allMatch(File::exists);
	}

	public boolean isValidJob() {
		return jarFile.exists() && JarVerificator.isValidJar(jarFile);
	}

	public boolean hasValidMainClass() {
		return JarVerificator.hasClass(jarFile, job.mainClass);
	}
}

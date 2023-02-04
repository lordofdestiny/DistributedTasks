package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

public class JobRequestDescriptor {
	public static class JobCreationException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public JobCreationException(String message) {
			super(message);
		}
	}

	public static JobRequestDescriptor parse(File file)
			throws JobCreationException, FileNotFoundException, JsonSyntaxException, JsonIOException {
		Gson gson = new Gson();
		Reader reader = new FileReader(file);
		final var jd = gson.fromJson(reader, JobRequestDescriptor.class);
		if (jd == null) {
			throw new JobCreationException("Invalid file format");
		}
		jd.confFileDir = file.getParentFile();
		jd.confFileName = file.getName();
		jd.setValidFormat();
		jd.setValidFiles();
		jd.setValidJarName();
		jd.setValidJar();
		return jd;
	}

	private void resolveFileNames() {
		if (fromConstructor)
			return;
		confFileName = new File(confFileDir, confFileName).getAbsolutePath();
		jobJar = new File(confFileDir, jobJar).getAbsolutePath();
		for (int i = 0; i < files.in.length; i++) {
			files.in[i] = new File(confFileDir, files.in[i]).getAbsolutePath();
		}
	}

	public static List<Path> copyFilesToPath(Path destDir, JobRequestDescriptor jobDescriptor) throws IOException {
		jobDescriptor.resolveFileNames();
		Path srcDir = jobDescriptor.confFileDir.toPath();

		List<Path> copies = new LinkedList<>();
		
		// Copy manifest
		Path confFileSrc = Path.of(jobDescriptor.confFileName);
		Path confFileDest = destDir.resolve(srcDir.relativize(confFileSrc).resolveSibling("manifest.json"));
		copies.add(confFileDest);
		Files.copy(confFileSrc, confFileDest, StandardCopyOption.REPLACE_EXISTING);

		// Copy JAR
		Path jarFileSrc = Path.of(jobDescriptor.jobJar);
		Path jarFileDest = destDir.resolve(srcDir.relativize(jarFileSrc));
		copies.add(jarFileDest);
		Files.copy(jarFileSrc, jarFileDest, StandardCopyOption.REPLACE_EXISTING);
				
		for(String fileName : jobDescriptor.files.in) {
			Path source = Path.of(fileName);
			Path destination = destDir.resolve(srcDir.relativize(source));
			copies.add(destination);
			Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		
		return copies;
	}

	public static boolean generate(File location, JobRequestDescriptor jobDescriptor) {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.serializeNulls();
		Gson gson = gsonBuilder.create();
		try (FileWriter writer = new FileWriter(location)) {
			gson.toJson(jobDescriptor, writer);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public JobRequestDescriptor() {
	}

	public JobRequestDescriptor(String name, File jobJar, String mainClass, String args, ArrayList<String> inFiles,
			ArrayList<String> outFiles) throws JobCreationException {
		if (!jobJar.exists() || !JarVerificator.isValidJar(jobJar)
				|| !JarVerificator.hasClass(jobJar, mainClass, false)) {
			throw new JobCreationException("Bad JAR file");
		}
		this.name = name;
		this.jobJar = jobJar.getName();
		this.mainClass = mainClass;
		this.args = args != null ? args.replaceAll("\\s+", " ").split(" ") : new String[0];
		this.files.in = inFiles != null
				? inFiles.stream().filter(Objects::nonNull).filter(String::isBlank).toArray(String[]::new)
				: new String[0];
		this.files.out = outFiles != null
				? outFiles.stream().filter(Objects::nonNull).filter(String::isBlank).toArray(String[]::new)
				: new String[0];
		this.fromConstructor = true;
	}

	public static class JobFiles {
		String[] in = {};
		String[] out = {};

		public String[] getIn() {
			return in;
		}

		public String[] getOut() {
			return out;
		}
	}

	transient File confFileDir;
	transient String confFileName;

	@SerializedName("job")
	String jobJar;
	String name;
	String mainClass;
	String[] args = {};

	transient boolean validFormat;
	transient boolean validFiles;
	transient boolean validJarName;
	transient boolean validArchive;
	transient boolean validClassName;
	transient boolean fromConstructor = false;

	JobFiles files = new JobFiles();

	private void setValidFormat() {
		validFormat = mainClass != null && jobJar != null
				&& ((files == null) || (files != null && files.in.length <= 6 && files.out.length <= 6));
	}

	private void setValidFiles() {
		validFiles = Stream.of(files.in).map(name -> new File(confFileDir, name)).allMatch(File::exists);
	}

	private void setValidJarName() {
		validJarName = new File(confFileDir, jobJar).exists();
	}

	private void setValidJar() {
		File jarFile = new File(confFileDir, jobJar);
		validArchive = JarVerificator.isValidJar(jarFile);
		validClassName = JarVerificator.hasClass(jarFile, mainClass);
	}

	public boolean isFormatValid() {
		return validFormat;
	}

	public boolean isValidJob() {
		return validJarName && validArchive;
	}

	public boolean hasClass() {
		return validClassName;
	}

	public boolean validInFiles() {
		return validFiles;
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

	public JobFiles getFiles() {
		return files;
	}

}

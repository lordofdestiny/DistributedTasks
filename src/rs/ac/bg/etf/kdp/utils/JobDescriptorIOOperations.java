package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JobDescriptorIOOperations {
	private static Path resolve(Path srcDir, Path destDir, Path file) {
		return destDir.resolve(srcDir.relativize(file));
	}

	public static List<Path> copyFilesToPath(Path destDir, JobDescriptor jobDescriptor)
			throws IOException {
		final var desc = JobDescriptor.resolveFileNames(jobDescriptor);

		final var destinations = new ArrayList<Path>(8);
		// Copy manifest and JAR
		final var confFileSrc = Path.of(desc.confFileName);
		final var jarFileSrc = Path.of(desc.jobJar);
		final var confFileDest = resolve(desc.confFileDir, destDir, confFileSrc);
		final var jarFileDest = resolve(desc.confFileDir, destDir, jarFileSrc);
		destinations.add(jarFileDest);
		destinations.add(confFileDest);
		Files.copy(confFileSrc, confFileDest, REPLACE_EXISTING);
		Files.copy(jarFileSrc, jarFileDest, REPLACE_EXISTING);

		// Copy input files
		try (final var files = Stream.of(desc.files.in).map(Path::of)) {
			for (final var srcFile : (Iterable<Path>) files::iterator) {
				Path dest = resolve(desc.confFileDir, destDir, srcFile);
				destinations.add(dest);
				Files.copy(srcFile, dest, REPLACE_EXISTING);
			}

		}

		return destinations;
	}

	public static boolean generate(File location, JobDescriptor jobDescriptor) {
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

	public static void deleteTempDirectory(Path dir) throws IOException {
		for (final var file : dir.toFile().listFiles()) {
			file.delete();
		}
		Files.delete(dir);
	}

	public static class TemporaryFiles {
		private File zipFile;
		private Path tempDirectory;

		public TemporaryFiles(File zip, Path dir) {
			zipFile = zip;
			tempDirectory = dir;
		}

		public File getZip() {
			return zipFile;
		}

		public Path getDirectory() {
			return tempDirectory;
		}
	}

	public static TemporaryFiles createTempZip(JobDescriptor jrd, Path temp) throws IOException {
		System.out.println(temp.toString());// debugging

		final var copyPaths = copyFilesToPath(temp, jrd);

		final var zipFile = new File(temp.toFile(), "job.zip");

		try (final var fos = new FileOutputStream(zipFile);
				final var zipOut = new ZipOutputStream(fos)) {
			for (final var path : copyPaths) {
				final var file = path.toFile();
				try (final var fis = new FileInputStream(file)) {
					final var entry = new ZipEntry(file.getName());
					zipOut.putNextEntry(entry);

					final var bytes = new byte[1024];
					int length;
					while ((length = fis.read(bytes)) >= 0) {
						zipOut.write(bytes, 0, length);
					}
				}
			}
		} catch (IOException e) {
			deleteTempDirectory(temp);
		}
		return new TemporaryFiles(zipFile, temp);
	}

}

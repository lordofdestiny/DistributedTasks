package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FileOperations {
	private static final int BUFFER_SIZE = 4096;

	public static boolean generate(File location, JobDescriptor jobDescriptor) {
		Objects.requireNonNull(location);
		Objects.requireNonNull(jobDescriptor);
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
		Objects.requireNonNull(dir);
		for (final var file : dir.toFile().listFiles()) {
			file.delete();
		}
		Files.delete(dir);
	}

	public static boolean deleteDirectory(Path dir) throws IOException {
		return Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile).map(File::delete)
				.allMatch(b -> b);
	}

	public static void zip(List<Path> files, File zipFile) throws IOException {
		Objects.requireNonNull(files);
		Objects.requireNonNull(zipFile);

		try (final var fos = new FileOutputStream(zipFile);
				final var zipOut = new ZipOutputStream(fos)) {
			for (final var path : files) {
				final var file = path.toFile();
				try (final var fis = new FileInputStream(file)) {
					final var entry = new ZipEntry(file.getName());
					zipOut.putNextEntry(entry);

					final var buffer = new byte[BUFFER_SIZE];
					int length;
					while ((length = fis.read(buffer)) >= 0) {
						zipOut.write(buffer, 0, length);
					}
				}
			}
		}
	}

	public static List<Path> unzip(File zipFile, Path destDir) throws IOException {
		Objects.requireNonNull(zipFile);
		try (final var fis = new FileInputStream(zipFile);
				final var zis = new ZipInputStream(fis)) {
			for (var entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
				final var newFile = destDir.resolve(entry.getName()).toFile();
				if (entry.isDirectory()) {
					if (!newFile.isDirectory() && !newFile.mkdirs()) {
						throw new IOException(
								String.format("Failed to create directory %s", newFile));
					}
					continue;
				}

				final var parent = newFile.getParentFile();
				if (!parent.isDirectory() && !parent.mkdirs()) {
					throw new IOException(String.format("Failed to create directory %s", parent));
				}

				try (final var fos = new FileOutputStream(newFile)) {
					int len;
					final var buffer = new byte[BUFFER_SIZE];
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
				}
			}
		}

		return null;
	}

}

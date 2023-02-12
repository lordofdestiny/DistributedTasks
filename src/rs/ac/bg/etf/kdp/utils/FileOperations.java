package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
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

	public static void createZip(File file) throws IOException {
		try (final var fos = new FileOutputStream(file); final var zos = new ZipOutputStream(fos)) {
		}
	}

	public static void addFileToZip(Path zipFile, String zipDestPath, File srcFile)
			throws IOException {
		final var uri = URI.create("jar:" + zipFile.toUri());
		final var env = new HashMap<String, String>();
		env.put("create", "true");

		int length;
		final var buffer = new byte[BUFFER_SIZE];
		try (final var fs = FileSystems.newFileSystem(uri, env)) {
			final var zipPath = fs.getPath(zipDestPath);
			if (Files.exists(zipPath)){
				return;
			}
			if (zipPath.getParent() != null) {
				Files.createDirectories(zipPath.getParent());
			}

			try (final var fos = Files.newOutputStream(zipPath, StandardOpenOption.CREATE_NEW);
					final var fis = new FileInputStream(srcFile)) {
				while ((length = fis.read(buffer)) >= 0) {
					fos.write(buffer, 0, length);
				}

			}
		}
	}

	public static void addFolderToZip(Path zipFile, String zipDestPath, Path folderSrcPath)
			throws IOException {
		final var uri = URI.create("jar:" + zipFile.toUri());
		final var env = new HashMap<String, String>();
		env.put("create", "true");

		int length;
		final var buffer = new byte[BUFFER_SIZE];

		try (final var fs = FileSystems.newFileSystem(uri, env);
				final var files = Files.walk(folderSrcPath)) {
			final var destRoot = fs.getPath(zipDestPath);

			for (final var file : (Iterable<Path>) files::iterator) {
				final var zipPath = destRoot
						.resolve(fs.getPath(folderSrcPath.relativize(file).toString()));

				Files.createDirectories(zipPath.getParent());
				if (file.toFile().isDirectory()) {
					continue;
				}
				try (final var fos = Files.newOutputStream(zipPath, StandardOpenOption.CREATE_NEW);
						final var fis = new FileInputStream(file.toFile())) {
					while ((length = fis.read(buffer)) >= 0) {
						fos.write(buffer, 0, length);
					}
				}
			}
		}
	}

	public static void zipFileList(List<Path> files, File zipFile) throws IOException {
		Objects.requireNonNull(files);
		Objects.requireNonNull(zipFile);

		final var buffer = new byte[BUFFER_SIZE];
		try (final var fos = new FileOutputStream(zipFile);
				final var zipOut = new ZipOutputStream(fos)) {
			for (final var path : files) {
				final var file = path.toFile();
				try (final var fis = new FileInputStream(file)) {
					final var entry = new ZipEntry(file.getName());
					zipOut.putNextEntry(entry);

					int length;
					while ((length = fis.read(buffer)) >= 0) {
						zipOut.write(buffer, 0, length);
					}
				}
			}
		}
	}

	public static void zipDirectory(Path source, File destination) throws IOException {
		Objects.requireNonNull(source);
		Objects.requireNonNull(destination);

		class Pair {
			File source;
			String pathInZip;

			Pair(File src, String piz) {
				source = src;
				pathInZip = piz;
			}
		}

		final var pairs = Files.walk(source).map(Path::toFile).map((file) -> {
			var name = source.relativize(file.toPath()).toFile().toString();
			if (file.isDirectory() && !name.endsWith(File.separator)) {
				name += File.separator;
			}
			return new Pair(file, name);
		});

		final var buffer = new byte[BUFFER_SIZE];
		try (final var fos = new FileOutputStream(destination);
				final var zipOut = new ZipOutputStream(fos)) {

			for (final var pair : (Iterable<Pair>) pairs::iterator) {
				zipOut.putNextEntry(new ZipEntry(pair.pathInZip));
				if (pair.source.isDirectory()) {
					continue;
				}
				try (final var fis = new FileInputStream(pair.source)) {
					int length;
					while ((length = fis.read(buffer)) >= 0) {
						zipOut.write(buffer, 0, length);
					}
				}
			}
		}
	}

	private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}

	public static void unzip(File zipFile, Path destDir, boolean deleteAfterExtraction)
			throws IOException {
		Objects.requireNonNull(zipFile);
		try (final var fis = new FileInputStream(zipFile);
				final var zis = new ZipInputStream(fis)) {
			for (var entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
				final var newFile = newFile(destDir.toFile(), entry);
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

				if (newFile.isDirectory()) {
					continue;
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
		if (deleteAfterExtraction) {
			Files.delete(zipFile.toPath());
		}
	}

	public static void unzip(File zipFile, Path destDir) throws IOException {
		unzip(zipFile, destDir, false);
	}

	public static void unzipAllInTree(Path location) throws IOException {
		unzipAllInTree(location, (path) -> {
			final var zipName = path.toFile().getName();
			final var baseName = zipName.substring(0, zipName.lastIndexOf("."));
			return path.resolveSibling(baseName);
		});
	}

	public static void unzipAllInTree(Path location, UnaryOperator<Path> destNameMaker)
			throws IOException {
		unzipAllInTree(location, destNameMaker, false);
	}

	public static void unzipAllInTree(Path location, UnaryOperator<Path> destNameMaker,
			boolean deleteAfterExtraction) throws IOException {
		final var resultsStream = Files.walk(location)
				.filter(path -> path.toFile().getName().endsWith(".zip"));

		for (final var zipFilePath : (Iterable<Path>) resultsStream::iterator) {
			final var destPath = destNameMaker.apply(zipFilePath);
			Files.createDirectories(destPath);
			unzip(zipFilePath.toFile(), destPath, deleteAfterExtraction);
		}
	}

}

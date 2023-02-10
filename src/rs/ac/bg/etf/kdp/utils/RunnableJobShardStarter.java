package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;

public class RunnableJobShardStarter {

	public static void main(String[] args) {
		if (args.length < 2) {
			throw new RuntimeException("improper use of this class.");
		}

		final var argsFile = new File(args[0]);
		Runnable task = null;
		try (final var is = new FileInputStream(argsFile);
				final var ois = new ObjectInputStream(is);) {
			task = (Runnable) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}

		try {
			Files.delete(argsFile.toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}

		final var thread = new Thread(task, args[1]);
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

}

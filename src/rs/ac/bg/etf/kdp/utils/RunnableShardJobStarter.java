package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RunnableShardJobStarter {

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

		AtomicBoolean excepionThrown = new AtomicBoolean(false);
		AtomicReference<Throwable> exception = new AtomicReference<>();
		final var thread = new Thread(task, args[1]);
		thread.setUncaughtExceptionHandler((t, e) -> {
			excepionThrown.set(true);
			exception.set(e);
		});
		thread.start();
		try {
			thread.join();
			if (excepionThrown.get()) {
				throw new RuntimeException(exception.get());
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

}

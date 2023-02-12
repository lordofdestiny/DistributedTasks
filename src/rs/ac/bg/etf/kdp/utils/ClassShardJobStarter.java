package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import rs.ac.bg.etf.kdp.core.IWorkerServer.JobShardArgs;

public class ClassShardJobStarter {

	public static void main(String[] args) {
		if (args.length < 1) {
			throw new RuntimeException("improper use of this class.");
		}

		final var argsFile = new File(args[0]);
		JobShardArgs sargs = null;
		try (final var is = new FileInputStream(argsFile);
				final var ois = new ObjectInputStream(is);) {
			sargs = (JobShardArgs) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}

		try {
			Files.delete(argsFile.toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			AtomicBoolean excepionThrown = new AtomicBoolean(false);
			AtomicReference<Throwable> exception = new AtomicReference<>();

			final var invk = new ClassMethodInvoker(sargs.getClassName(), sargs.getCtorArgs(),
					sargs.getMethodName(), sargs.getMethodArgs());
			invk.setUncaughtExceptionHandler((t, e) -> {
				excepionThrown.set(true);
				exception.set(e);
			});
			invk.start();
			invk.join();
			if (excepionThrown.get()) {
				throw new RuntimeException(exception.get());
			}
		} catch (ReflectiveOperationException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}

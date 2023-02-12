package rs.ac.bg.etf.kdp.core.worker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.RunnableShardJobStarter;

public class RunnableShardJobTask extends JobTask {

	public RunnableShardJobTask(WorkerJobRecord record, ConnectionInfo info, String name,
			Runnable task) throws IOException {
		super(record, info);
		final var argBin = record.getWorkingDir().resolve("args.bin").toFile();
		try (final var os = new FileOutputStream(argBin);
				final var oos = new ObjectOutputStream(os)) {
			oos.writeObject(task);
		}
		pb.command().add(name);
	}

	@Override
	protected List<String> buildCommand() {
		final List<String> list = new ArrayList<>(10);
		list.add("java");
		list.add("-cp");
		list.add(jarFile.toString());
		list.add(RunnableShardJobStarter.class.getCanonicalName());
		list.add("args.bin");
		return list;
	}

}

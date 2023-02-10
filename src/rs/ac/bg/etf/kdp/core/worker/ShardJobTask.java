package rs.ac.bg.etf.kdp.core.worker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import rs.ac.bg.etf.kdp.core.IWorkerServer.JobShardArgs;
import rs.ac.bg.etf.kdp.utils.JobShardStarter;

public class ShardJobTask extends JobTask {
	public ShardJobTask(WorkerJobRecord record, JobShardArgs args) throws IOException {
		super(record);
		final var argsBin = record.getWorkingDir().resolve("args.bin").toFile();
		try (final var os = new FileOutputStream(argsBin);
				final var oos = new ObjectOutputStream(os)) {
			oos.writeObject(args);
		}
	}

	protected List<String> buildCommand() {
		final List<String> list = new ArrayList<>(10);
		list.add("java");
		list.add("-cp");
		list.add(jarFile.toString());
		list.add(JobShardStarter.class.getCanonicalName());
		list.add("args.bin");
		return list;
	}
}

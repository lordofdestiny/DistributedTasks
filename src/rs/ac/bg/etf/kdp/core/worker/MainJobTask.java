package rs.ac.bg.etf.kdp.core.worker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainJobTask extends JobTask{
	public MainJobTask(WorkerJobRecord record) throws IOException {
		super(record);
	}

	protected List<String> buildCommand() {
		final var job = record.getJobDescriptor();
		final List<String> list = new ArrayList<>(10);
		list.add("java");
		list.add("-cp");
		list.add(jarFile.toString());
		list.add(job.getMainClassName());
		list.addAll(List.of(job.getArgs()));
		return list;
	}
}

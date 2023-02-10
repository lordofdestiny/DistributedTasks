package rs.ac.bg.etf.kdp.core.server;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

import rs.ac.bg.etf.kdp.core.server.ServerJobRecord.JobStatus;

public class JobScheduler extends Thread {
	private final BlockingQueue<WorkerRecord> workers = new LinkedBlockingQueue<>();
	private final BlockingQueue<ServerJobRecord> jobs = new LinkedBlockingQueue<>();
	private BiConsumer<WorkerRecord, ServerJobRecord> handler = null;

	public void putJob(ServerJobRecord record) {
		record.setStatus(JobStatus.READY);
		jobs.offer(record);
	}

	public void putWorker(WorkerRecord record, boolean failed) {
		if (record.isOnline() && !workers.contains(record)) {
			record.decreaseActiveJobs();
			workers.offer(record);
			return;
		}
	}

	public void putWorker(WorkerRecord record) {
		putWorker(record, false);
	}

	public void setHandler(BiConsumer<WorkerRecord, ServerJobRecord> handler) {
		this.handler = Objects.requireNonNull(handler);
	}

	public void quit() {
		interrupt();
	}

	@Override
	public void run() {
		if (handler == null) {
			throw new RuntimeException(
					"You have to set the error handler before starting the scheduler");
		}
		mainLoop:
		while (true) {
			ServerJobRecord job = null;
			WorkerRecord worker = null;
			try {
				job = jobs.take();
			} catch (InterruptedException e) {
				break;
			}
			System.out.printf("Job %s ready!\n", job.jobUUID);
			do {
				try {
					worker = workers.take();
				} catch (InterruptedException e) {
					break mainLoop;
				}
			} while (!worker.isOnline());
			System.out.printf("Worker %s ready!\n", worker.getUUID());
			job.setStatus(JobStatus.SCHEDULED);
			worker.increaseActiveJobs();
			if (worker.getConcurrency() > 0) {
				putWorker(worker);
			}
			handler.accept(worker, job);
		}
		System.out.println("Scheduler shutdown...");
	}
}

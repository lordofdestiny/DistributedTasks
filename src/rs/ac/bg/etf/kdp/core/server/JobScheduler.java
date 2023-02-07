package rs.ac.bg.etf.kdp.core.server;

import java.rmi.RemoteException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.BiConsumer;

public class JobScheduler extends Thread {
    private final BlockingQueue<WorkerRecord> workers =
            new PriorityBlockingQueue<WorkerRecord>(10, WorkerRecord::compare);
    private final BlockingQueue<JobRecord> jobs = new LinkedBlockingQueue<>();
    private BiConsumer<WorkerRecord, JobRecord> handler = null;

    public void putJob(JobRecord record) {
        jobs.offer(record);
    }

    public void putWorker(WorkerRecord record) {
        if (!workers.contains(record)) {
            workers.offer(record);
        }
    }

    public JobScheduler(BiConsumer<WorkerRecord, JobRecord> handler) {
        this.handler = Objects.requireNonNull(handler);
        start();
    }

    public void quit() {
        interrupt();
    }

    @Override
    public void run() {
        while (true) {
            JobRecord job = null;
            WorkerRecord worker = null;
            try {
                job = jobs.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
            System.out.printf("Job %s ready!\n",job.jobUUID);
            do {
                try {
                    worker = workers.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            } while (worker != null && !worker.isOnline());
            System.out.printf("Worker %s ready!\n",worker.uuid);
            handler.accept(worker,job);
        }
    }
}

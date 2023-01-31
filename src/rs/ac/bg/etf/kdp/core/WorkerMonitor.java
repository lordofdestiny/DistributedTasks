package rs.ac.bg.etf.kdp.core;

import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class WorkerMonitor extends TimerTask {
    private final ScheduledThreadPoolExecutor pool;
    private final CentralServer server;
    private CountDownLatch latch;

    WorkerMonitor(CentralServer server) {
        this.server = server;
        pool = new ScheduledThreadPoolExecutor(100);
    }

    @Override
    public void run() {
        latch = new CountDownLatch(server.registeredWorkers.size());
        server.registeredWorkers.keySet().stream()
                .map((wid) -> (Runnable) () -> monitorWorker(wid))
                .forEach(task -> pool.schedule(task, 0, TimeUnit.SECONDS));
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void monitorWorker(UUID workerId) {
        final var record = server.registeredWorkers.get(workerId);
        final var ping = record.getPing();
        final var id = record.getId();
        if (ping.isPresent()) {
            System.out.printf(record.isOnline()
                            ? "Ping to %s is %d ms\n"
                            : "Worker %s is online again! Ping %d ms\n",
                    id, ping.get()
            );
        } else {
            server.onlineWorkers.remove(id);
            if (record.isOnline()) {
                record.setOffline();
                System.err.printf("Worker %s failed and is offline!\n", id);
            }
        }
        latch.countDown();
    }
}
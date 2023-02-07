package rs.ac.bg.etf.kdp.core.server;

import rs.ac.bg.etf.kdp.core.IPingable;

public class WorkerMonitor extends Thread {
    private int interval = 5000;
    private WorkerRecord record;
    private WorkerStateListener listener;

    WorkerMonitor(WorkerRecord record, int interval, WorkerStateListener listener) {
        this.listener = listener;
        this.interval = interval;
        this.record = record;
    }

    @Override
    public void run() {
        while (true) {
            if(Thread.interrupted()){
                return;
            }
            final var wasOnline = record.isOnline();
            final var ping = IPingable.getPing(record.getHandle());
            if (ping.isPresent()) {
                if (wasOnline) {
                    listener.isConnected(record, ping.get());
                } else {
                    listener.reconnected(record, ping.get());
                }
            } else if (wasOnline) {
                listener.workerUnavailable(record);
            } else if(record.deadlineExpired()){
                listener.workerFailed(record);
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
            }
        }
    }

    public void quit() {
        interrupt();
    }
}

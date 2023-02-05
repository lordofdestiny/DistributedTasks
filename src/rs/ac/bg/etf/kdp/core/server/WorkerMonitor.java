package rs.ac.bg.etf.kdp.core.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import rs.ac.bg.etf.kdp.core.IPingable;

public class WorkerMonitor extends Thread {
	private int interval = 5000;
	private Supplier<WorkerRecord[]> workerSource;
	private List<WorkerStateListener> listeners;

	WorkerMonitor(Supplier<WorkerRecord[]> workers, int interval) {
		this.listeners = new ArrayList<>();
		this.workerSource = workers;
		this.interval = interval;
	}

	@Override
	public void run() {
		while (true) {
			if (Thread.interrupted()) {
				return;
			}
			Thread[] threads = Stream.of(workerSource.get()).map(this::makeMonitor).map(Thread::new)
					.toArray(Thread[]::new);
			Arrays.stream(threads).forEach(Thread::start);
			try {
				for (final var thread : threads) {
					thread.join();
				}
				Thread.sleep(this.interval);
			} catch (InterruptedException ignore) {
			}
		}
	}

	int getPingInterval() {
		return interval;
	}

	void setPingInterval(int interval) {
		this.interval = interval;
	}

	Runnable makeMonitor(WorkerRecord worker) {
		return () -> {
			final var wasOnline = worker.isOnline();
			final var ping = IPingable.getPing(worker.getHandle());
			if (ping.isPresent()) {
				if (wasOnline) {
					listeners.forEach(listener -> listener.isConnected(worker, ping.get()));
				} else {
					listeners.forEach(listener -> listener.reconnected(worker, ping.get()));
				}
			} else {
				if (worker.isOnline()) {
					listeners.forEach(listener -> listener.workerFaied(worker));
				}
				listeners.forEach(listener -> listener.notConnected(worker));
			}
		};
	}

	public void addWorkerStateListener(WorkerStateListener listener) {
		listeners.add(listener);
	}

	public void removeWorkerStateListener(WorkerStateListener listener) {
		listeners.remove(listener);
	}

	public void quit() {
		interrupt();
	}
}

package rs.ac.bg.etf.kdp.core.server;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rs.ac.bg.etf.kdp.core.IServerWorker.WorkerRegistration;
import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.Configuration;

class WorkerRecord {
	public static enum WorkerState {
		ONLINE, UNAVAILABLE, OFFLINE;
	}

	private UUID uuid;
	private Lock stateLock = new ReentrantLock();
	private WorkerState state;
	private IWorkerServer handle;

	// Scheduler
	private int maxConcurrency;
	private AtomicInteger availableConcurrency;

	// Monitoring
	private AtomicReference<WorkerMonitor> monitor = new AtomicReference<>();
	private Instant deadline = null;

	
	private List<UUID> assignedJobs = new CopyOnWriteArrayList<>();

	WorkerRecord(WorkerRegistration registration) {
		this.state = WorkerState.ONLINE;
		this.uuid = registration.getUUID();
		this.handle = registration.getHandle();
//		this.maxConcurrency = registration.getConcurrency();
		this.maxConcurrency = 10;
		this.availableConcurrency = new AtomicInteger(maxConcurrency);
	}

	public UUID getUUID() {
		return uuid;
	}

	public void setState(WorkerState state) {
		stateLock.lock();
		try {
			this.state = state;
		} finally {
			stateLock.unlock();
		}
	}

	public WorkerState getState() {
		stateLock.lock();
		try {
			return state;
		} finally {
			stateLock.lock();
		}
	}

	public boolean isOnline() {
		stateLock.lock();
		try {
			return Objects.equals(state, WorkerState.ONLINE);
		} finally {
			stateLock.unlock();
		}
	}

	public IWorkerServer getHandle() {
		return handle;
	}

	public int getMaxConcurrency() {
		return maxConcurrency;
	}

	public int getConcurrency() {
		return availableConcurrency.get();
	}

	public int increaseActiveJobs() {
		return availableConcurrency.updateAndGet(value -> {
			if (value < maxConcurrency) {
				return value + 1;
			} else {
				return value;
			}
		});
	}

	public int decreaseActiveJobs() {
		return availableConcurrency.updateAndGet(value -> {
			if (value > 0) {
				return value - 1;
			} else {
				return value;
			}
		});
	}

	public void setDeadline() {
		deadline = Instant.now().plusSeconds(Configuration.WORKER_DEADLINE_INTERVAL);
	}

	public boolean deadlineExpired() {
		return Instant.now().isAfter(deadline);
	}

	public void initializeMonitor(WorkerStateListener listener, int interval) {
		if (monitor != null) {
			return;
		}
		Objects.requireNonNull(listener);
		this.monitor.set(new WorkerMonitor(this, listener, interval));
		this.monitor.get().start();
	}

	public WorkerMonitor getMonitor() {
		return monitor.get();
	}

	public void killMonitor() {
		monitor.getAndUpdate((monitor) -> {
			if (monitor != null && !monitor.isKilled()) {
				monitor.quit();
			}
			return null;
		});
	}

	public void addAssignedJob(UUID jobUUID) {
		assignedJobs.add(jobUUID);
	}

	public boolean removeAssignedJob(UUID jobUUID) {
		return assignedJobs.remove(jobUUID);
	}

	public List<UUID> getAssignedJobs() {
		return assignedJobs;

	}
}
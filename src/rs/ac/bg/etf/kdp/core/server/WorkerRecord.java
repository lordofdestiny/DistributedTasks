package rs.ac.bg.etf.kdp.core.server;

import java.time.Instant;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.Configuration;

class WorkerRecord {
	public enum WorkerState {
		ONLINE, UNAVAILABLE, OFFLINE;
	}

	UUID uuid;
	WorkerState state;
	IWorkerServer handle;

	private int maxConcurrency;
	private int availableConcurrency;

	private WorkerMonitor monitor;
	private Instant deadline = null;

	WorkerRecord(UUID uuid, IWorkerServer worker,int concurrency) {
		this.uuid = uuid;
		this.handle = worker;
		this.state = WorkerState.ONLINE;
		this.maxConcurrency = concurrency;
		this.availableConcurrency = concurrency;
	}

	public static int compare(WorkerRecord first, WorkerRecord second) {
		return second.availableConcurrency - first.maxConcurrency;
	}

	public int getMaxConcurrency() {
		return maxConcurrency;
	}

	public int getConcurrency() {
		return availableConcurrency;
	}

	public void setDeadline() {
		deadline = Instant.now().plusSeconds(Configuration.WORKER_DEADLINE_INTERVAL);
	}

	public boolean deadlineExpired() {
		return Instant.now().isAfter(deadline);
	}

	public UUID getUUID() {
		return uuid;
	}

	public IWorkerServer getHandle() {
		return handle;
	}

	public boolean isOnline() {
		return state == WorkerState.ONLINE;
	}

	public static boolean isOnline(WorkerRecord record){
		return record.isOnline();
	}

	public void setState(WorkerState state) {
		this.state = state;
	}

	public WorkerState getState() {
		return state;
	}

	public void initializeMonitor(int interval, WorkerStateListener listner) {
		this.monitor = new WorkerMonitor(this, interval, listner);
		this.monitor.start();
	}
}
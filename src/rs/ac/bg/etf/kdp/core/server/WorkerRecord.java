package rs.ac.bg.etf.kdp.core.server;

import java.time.Instant;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.Configuration;

class WorkerRecord {
	public enum WorkerState {
		ONLINE, UNAVAILABLE, OFFLINE
	}

	UUID uuid;
	WorkerState state;
	IWorkerServer handle;
	private boolean online;
	private Instant deadline = null;

	WorkerRecord(UUID uuid, IWorkerServer worker) {
		this.uuid = uuid;
		this.handle = worker;
		this.online = true;
		state = WorkerState.ONLINE;
	}

	public void setDeadline() {
		deadline = Instant.now().plusSeconds(Configuration.WORKER_DEADLINE_INTERVAL);
	}

	public boolean deadlineExpired() {
		return deadline.isBefore(Instant.now());
	}

	public UUID getUUID() {
		return uuid;
	}

	public IWorkerServer getHandle() {
		return handle;
	}

	public boolean isOnline() {
		return online;
	}

	public void setState(WorkerState state) {
		this.state = state;
	}

	public WorkerState getState() {
		return state;
	}
}
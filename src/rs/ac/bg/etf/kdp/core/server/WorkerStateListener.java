package rs.ac.bg.etf.kdp.core.server;

public interface WorkerStateListener {
	void workerUnavailable(WorkerRecord worker);

	void isConnected(WorkerRecord worker, long ping);

	void reconnected(WorkerRecord worker, long ping);

	void workerFailed(WorkerRecord worker);
}

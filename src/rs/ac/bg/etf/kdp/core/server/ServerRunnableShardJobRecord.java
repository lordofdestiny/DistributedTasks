package rs.ac.bg.etf.kdp.core.server;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class ServerRunnableShardJobRecord extends ServerJobRecord {

	private String name;
	private Runnable task;

	public ServerRunnableShardJobRecord(UUID userUUID, UUID mainJobUUID, UUID jobUUID, String name, Runnable task)
			throws IOException {
		super(userUUID, mainJobUUID, jobUUID);
		this.name = name;
		this.task = task;
	}

	@Override
	protected FileUploadHandle postToWorker(IWorkerServer worker)
			throws RemoteException, RemoteIOException {
		return worker.scheduleRunnableJobShard(userUUID, mainJobUUID, jobUUID, name, task);
	}

}

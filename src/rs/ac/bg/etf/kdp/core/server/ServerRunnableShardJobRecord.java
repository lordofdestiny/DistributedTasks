package rs.ac.bg.etf.kdp.core.server;

import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.core.JobAuthenticator;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class ServerRunnableShardJobRecord extends ServerJobRecord {

	private String name;
	private Runnable task;

	public ServerRunnableShardJobRecord(UUID userUUID, UUID mainJobUUID, UUID parentJobUUID,
			UUID jobUUID, String name, Runnable task, Path directory) throws IOException {
		super(userUUID, mainJobUUID, parentJobUUID, jobUUID, directory);
		this.name = name;
		this.task = task;
	}

	@Override
	protected FileUploadHandle postToWorker(IWorkerServer worker)
			throws RemoteException, RemoteIOException {
		return worker.scheduleRunnableJobShard(
				new JobAuthenticator(userUUID, mainJobUUID, parentJobUUID, jobUUID), name, task);
	}

	@Override
	protected String description() {
		return String.format("Runnable; Name: %s", name);
	}

}

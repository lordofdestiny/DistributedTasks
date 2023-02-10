package rs.ac.bg.etf.kdp.core.server;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.core.IWorkerServer.JobShardArgs;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class ServerShardJobRecord extends ServerJobRecord {
	JobShardArgs args;

	ServerShardJobRecord(UUID userUUID, UUID mainJobUUID, UUID jobUUID, JobShardArgs args) throws IOException {
		super(userUUID, mainJobUUID, jobUUID);
		this.args = args;
	}

	@Override
	protected FileUploadHandle postToWorker(IWorkerServer worker)
			throws RemoteException, RemoteIOException {
		return worker.scheduleJobShard(userUUID, mainJobUUID, jobUUID, args);
	}

}

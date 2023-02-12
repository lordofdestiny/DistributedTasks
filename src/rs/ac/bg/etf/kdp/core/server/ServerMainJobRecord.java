package rs.ac.bg.etf.kdp.core.server;

import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class ServerMainJobRecord extends ServerJobRecord {

	ServerMainJobRecord(UUID userUUID, UUID jobUUID, Path directory) throws IOException {
		super(userUUID, jobUUID, null, jobUUID, directory);
	}

	@Override
	protected FileUploadHandle postToWorker(IWorkerServer worker)
			throws RemoteException, RemoteIOException {
		return worker.scheduleMainJob(userUUID, jobUUID);
	}

	@Override
	protected String description() {
		return String.format("main-job");
	}

}

package rs.ac.bg.etf.kdp.core.server;

import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.core.IWorkerServer.JobShardArgs;
import rs.ac.bg.etf.kdp.core.JobAuthenticator;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public class ServerClassShardJobRecord extends ServerJobRecord {
	JobShardArgs args;

	ServerClassShardJobRecord(UUID userUUID, UUID mainJobUUID, UUID parentJobUUID, UUID jobUUID,
			JobShardArgs args, Path directory) throws IOException {
		super(userUUID, mainJobUUID, parentJobUUID, jobUUID, directory);
		this.args = args;
	}

	@Override
	protected FileUploadHandle postToWorker(IWorkerServer worker)
			throws RemoteException, RemoteIOException {
		return worker.scheduleJobShard(
				new JobAuthenticator(userUUID, mainJobUUID, parentJobUUID, jobUUID), args);
	}

	private static String joinWithComma(Object[] arr) {
		return Stream.of(arr).map(Object::toString).collect(Collectors.joining(", "));
	}

	@Override
	protected String description() {
		return String.format("%s (%s) :: %s (%s)", args.getClassName(),
				joinWithComma(args.getCtorArgs()), args.getMethodName(),
				joinWithComma(args.getMethodArgs()));
	}

}

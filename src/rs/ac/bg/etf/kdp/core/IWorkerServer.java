package rs.ac.bg.etf.kdp.core;

import java.io.Serializable;
import java.rmi.MarshalException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;

public interface IWorkerServer extends IPingable, Remote {
	FileUploadHandle scheduleMainJob(UUID userUUID, UUID jobUUID)
			throws RemoteException, RemoteIOException;

	public static class JobShardArgs implements Serializable {
		private static final long serialVersionUID = 1L;
		private final String className;
		private final Object[] ctorArgs;
		private final String methodName;
		private final Object[] methodArgs;

		public JobShardArgs(String className, Object[] ctorArgs, String methodName,
				Object[] methodArgs) {
			this.className = className;
			this.ctorArgs = ctorArgs;
			this.methodName = methodName;
			this.methodArgs = methodArgs;

		}

		public String getClassName() {
			return className;
		}

		public Object[] getCtorArgs() {
			return ctorArgs;
		}

		public String getMethodName() {
			return methodName;
		}

		public Object[] getMethodArgs() {
			return methodArgs;
		}
	}

	FileUploadHandle scheduleJobShard(UUID userUUID, UUID mainJobUUID, UUID jobUUID,
			JobShardArgs args) throws RemoteException, MarshalException, UnmarshalException;

	FileUploadHandle scheduleRunnableJobShard(UUID userUUID, UUID mainJobUUID, UUID jobUUID,
			String name, Runnable task) throws RemoteException;
}

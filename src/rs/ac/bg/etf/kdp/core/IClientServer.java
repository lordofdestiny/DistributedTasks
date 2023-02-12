package rs.ac.bg.etf.kdp.core;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.JobStatus;

public interface IClientServer extends Remote {

	public static class JobTreeNode implements Serializable {
		private static final long serialVersionUID = 1L;
		public JobStatus status;
		public UUID jobUUID;
		public String description;
		public JobTreeNode[] children;

		@Override
		public String toString() {
			return String.format("%s - %s - %s", description, status, jobUUID);
		}
	}

	FileUploadHandle submitCompleteJob(UUID mainJobUUID) throws RemoteException;

	void notifyJobFailedToStart(UUID jobUUID, String cause) throws RemoteException;

	void notifyJobFailed(JobTreeNode failSubtree) throws RemoteException;
}

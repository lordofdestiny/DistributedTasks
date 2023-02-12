package rs.ac.bg.etf.kdp.core;

import java.io.Serializable;
import java.util.UUID;

public class JobAuthenticator implements Serializable {
	private static final long serialVersionUID = 1L;

	public JobAuthenticator(UUID userUUID, UUID mainJobUUID, UUID parentJobUUID, UUID jobUUID) {
		this.userUUID = userUUID;
		this.mainJobUUID = mainJobUUID;
		this.parentJobUUID = parentJobUUID;
		this.jobUUID = jobUUID;
	}

	public UUID userUUID;
	public UUID mainJobUUID;
	public UUID parentJobUUID;
	public UUID jobUUID;
}
package rs.ac.bg.etf.kdp.core.server;

import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IClientServer;

class ClientRecord {
	UUID uuid;
	UUID currentJob;
	IClientServer handle;

	boolean online;

	ClientRecord(UUID uuid, IClientServer handle) {
		this.uuid = uuid;
		this.handle = handle;
	}

	boolean hasRegisteredJob() {
		return currentJob != null;
	}
}

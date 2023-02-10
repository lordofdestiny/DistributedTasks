package rs.ac.bg.etf.kdp.core.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rs.ac.bg.etf.kdp.core.IClientServer;

class ClientRecord {
	UUID uuid;
	UUID mainJobUUID;
	List<UUID> jobShards = new ArrayList<>();
	IClientServer handle;

	boolean online;

	ClientRecord(UUID uuid, IClientServer handle) {
		this.uuid = uuid;
		this.handle = handle;
	}

	boolean hasRegisteredJob() {
		return mainJobUUID != null;
	}
	
	boolean addShard(UUID shardUUID) {
		return jobShards.add(shardUUID);
	}
}

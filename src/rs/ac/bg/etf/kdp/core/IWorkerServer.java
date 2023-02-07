package rs.ac.bg.etf.kdp.core;

import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IWorkerServer extends IPingable, Remote {
    public FileUploadHandle uploadJob(UUID userUUID, UUID jobUUID) throws RemoteException;
}

package rs.ac.bg.etf.kdp.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.core.IServerWorker;
import rs.ac.bg.etf.kdp.core.IWorkerServer;
import rs.ac.bg.etf.kdp.core.server.WorkerRecord.WorkerState;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.FileDownloader;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingListener;

import javax.swing.filechooser.FileSystemView;

public class ServerProcess extends UnicastRemoteObject implements IServerWorker, IServerClient {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    static final Path CHANGE_LATER_ROOT_DIR = FileSystemView.getFileSystemView().getHomeDirectory()
            .toPath().resolve("Linda");

    static {
        Configuration.load();
    }

    private final Map<UUID, WorkerRecord> registeredWorkers = new ConcurrentHashMap<>();
    private final Map<UUID, ClientRecord> registeredClients = new ConcurrentHashMap<>();
    private final Map<UUID, JobRecord> allJobs = new ConcurrentHashMap<>();

    private ExecutorService jobUploaderThreadPool = Executors.newCachedThreadPool();

    private final JobScheduler scheduler = new JobScheduler((worker, job) -> {
        jobUploaderThreadPool.submit(()->{
            try {
                worker.handle.uploadJob(null, null);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        });
    });
    public ServerProcess() throws RemoteException {
        try {
            final var registry = LocateRegistry.createRegistry(Configuration.SERVER_PORT);
            registry.rebind(Configuration.SERVER_ROUTE, this);
            System.out.printf("Server started on port %s", Configuration.SERVER_PORT);
            ServerProcess.setLog(System.out);
        } catch (RemoteException e) {
            System.err.println("Failed to start central server!");
            System.err.println(e.getMessage());
            System.exit(0);
        }
    }

    @Override
    public void register(UUID id, IWorkerServer worker, int concurrency) throws AlreadyRegisteredException,
            RemoteException {
        if (registeredWorkers.containsKey(id)) {
            throw new AlreadyRegisteredException();
        }
        final var record = new WorkerRecord(id, worker, concurrency);
        record.initializeMonitor(Configuration.SERVER_PING_INTERVAL, new WorkerStateListener() {
            boolean firstFail = true;

            private final String DATE_FORMAT_STR = "dd.MM.YYYY. HH:mm:ss";
            private final DateFormat df = new SimpleDateFormat(DATE_FORMAT_STR);

            private String now() {
                return df.format(new Date());
            }

            @Override
            public void workerUnavailable(WorkerRecord worker) {
                worker.setState(WorkerState.UNAVAILABLE);
                System.err.printf("[%s] Worker %s unavailable!\n", now(), worker.uuid);
                worker.setDeadline();
            }

            @Override
            public void reconnected(WorkerRecord worker, long ping) {
                scheduler.putWorker(worker);
                worker.setState(WorkerState.ONLINE);
                System.out.printf("[%s] Worker %s is online again! Ping %d ms\n", now(), worker.uuid, ping);
            }

            @Override
            public void workerFailed(WorkerRecord worker) {
                if (worker.isOnline()) {
                    worker.setState(WorkerState.OFFLINE);
                    System.err.printf("[%s] Worker UUID %s is offline!", now(), worker.uuid);
                }
            }

            @Override
            public void isConnected(WorkerRecord worker, long ping) {
                worker.setState(WorkerState.ONLINE);
                System.out.printf("[%s] Ping to %s is %d ms\n", now(), worker.uuid, ping);
            }
        });

        registeredWorkers.put(id, record);
        try {
            worker.ping();
            scheduler.putWorker(record);
            System.out.printf("Worker %s is online!\n", id);
        } catch (RuntimeException e) {
            record.setState(WorkerState.OFFLINE);
            System.err.printf("Worker %s failed and is offline!\n", id);
        }
    }

    @Override
    public void register(UUID id, IClientServer clientHandle) {
        final var client = registeredClients.get(id);
        if (client != null) {
            client.online = true;
        } else {
            final var record = new ClientRecord(id, clientHandle);
            registeredClients.put(id, record);
        }
        System.out.printf("Client %s is online!\n", id);
    }

    @Override
    public void unregister(UUID id) throws RemoteException, UnregisteredClientException {
        ClientRecord client = registeredClients.get(id);
        if (client == null) {
            throw new UnregisteredClientException();
        }
        client.online = false;
        System.out.printf("Client %s is offline!\n", id);
    }


    @Override
    public void ping(UUID id) throws RemoteException {
        final var record = registeredWorkers.get(id);
        if (record != null) {
            record.setState(WorkerState.ONLINE);
        } else if (!registeredClients.containsKey(id)) {
            // Not a worker and not a client
            throw new UnknownUUIDException();
        }
    }

    @Override
    public void ping() {
        // Do nothing
    }

    @Override
    public FileUploadHandle registerJob(UUID userId) throws RemoteException, UnregisteredClientException, MultipleJobsException {
        ClientRecord client = registeredClients.get(userId);
        if (client == null) {
            throw new UnregisteredClientException();
        }
        if (client.hasRegisteredJob()) {
            throw new MultipleJobsException();
        }
        final var jobUUID = UUID.randomUUID();
        final var deadline = Instant.now().plus(2, ChronoUnit.MINUTES);
        final var record = new JobRecord(userId, jobUUID, deadline);
        client.currentJob = jobUUID;
        allJobs.put(jobUUID, record);

        final var downloader = new FileDownloader(record, new DownloadingListener() {
            private long total = 0;

            @Override
            public void onBytesReceived(int bytes) {
                total += bytes;
                System.out.printf("Job %s: Received %B so far...\n", record.jobUUID, total);
            }

            @Override
            public void onTransferComplete() {
                record.setStatus(JobRecord.JobStatus.READY);
                scheduler.putJob(record);
                // Job is ready to be sent or whatever we want
                // Handle by some other internal thread
                // Maybe signal it somehow
                System.out.printf("Job %s received. Size: %dKB\n", record.jobUUID, total / 1024);
            }

            @Override
            public void onDeadlineExceeded() {
                System.err.printf("Failed to receive job from %d.\n", record.userUUID);
                allJobs.remove(record.jobUUID);
                client.currentJob = null;
                try {
                    Files.walk(record.rootDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    System.err.println("Failed to cleanup after failed job transfer");
                }
            }
        });

        return new FileUploadHandle(downloader, deadline);
    }

    public static void main(String[] args) {
        try {
            @SuppressWarnings("unused") ServerProcess cs = new ServerProcess();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        // Thread or ThreadPool that manages job operations
    }
}

package rs.ac.bg.etf.kdp.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.filechooser.FileSystemView;

import rs.ac.bg.etf.kdp.core.IClientServer;
import rs.ac.bg.etf.kdp.core.IClientServer.JobTreeNode;
import rs.ac.bg.etf.kdp.core.IServerClient;
import rs.ac.bg.etf.kdp.core.IServerLinda;
import rs.ac.bg.etf.kdp.core.IServerWorker;
import rs.ac.bg.etf.kdp.core.IWorkerServer.JobShardArgs;
import rs.ac.bg.etf.kdp.core.JobAuthenticator;
import rs.ac.bg.etf.kdp.core.server.WorkerRecord.WorkerState;
import rs.ac.bg.etf.kdp.linda.CentralizedLinda;
import rs.ac.bg.etf.kdp.linda.Linda;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.FileDownloader;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.FileUploadHandle;
import rs.ac.bg.etf.kdp.utils.FileUploader;
import rs.ac.bg.etf.kdp.utils.FileUploader.UploadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingListener;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.DownloadingToken;
import rs.ac.bg.etf.kdp.utils.IFileDownloader.RemoteIOException;
import rs.ac.bg.etf.kdp.utils.JobStatus;

public class ServerProcess extends UnicastRemoteObject
		implements IServerWorker, IServerClient, IServerLinda {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	static final Path CHANGE_LATER_ROOT_DIR = FileSystemView.getFileSystemView().getHomeDirectory()
			.toPath().resolve("Linda");

	static {
		Configuration.load();
	}

	private ReentrantLock transactionLock = new ReentrantLock();
	private final Map<UUID, WorkerRecord> registeredWorkers = new ConcurrentHashMap<>();
	private final Map<UUID, ClientRecord> registeredClients = new ConcurrentHashMap<>();
	private ReentrantLock allJobsLock = new ReentrantLock();
	private final Map<UUID, ServerJobRecord> allJobs = new ConcurrentHashMap<>();

	private final ExecutorService generalTaskThreadPool = Executors.newFixedThreadPool(5);
	private final ExecutorService mainExecutorThreadPool = Executors.newCachedThreadPool();

	private final JobScheduler scheduler = new JobScheduler();

	private Map<UUID, ServerJobRecord> clientsWithCompletedJobs = new ConcurrentHashMap<>();
	private Map<UUID, ServerJobRecord> clientsWithKilledJobs = new ConcurrentHashMap<>();

	private final Linda linda = new CentralizedLinda();

	public ServerProcess() throws RemoteException {
		try {
			final var registry = LocateRegistry.createRegistry(Configuration.SERVER_PORT);
			registry.rebind(Configuration.SERVER_ROUTE, this);
			scheduler.setHandler((worker, job) -> {
				generalTaskThreadPool.submit(() -> jobReadyHandle(job, worker));
			});
			scheduler.start();

			System.out
					.println(String.format("Server started on port %s", Configuration.SERVER_PORT));
//			ServerProcess.setLog(System.out);
		} catch (RemoteException e) {
			System.err.println("Failed to start central server!");
			System.err.println(e.getMessage());
			System.exit(0);
		}
	}

	@Override
	public void register(WorkerRegistration registration)
			throws AlreadyRegisteredException, RemoteException {
		Objects.requireNonNull(registration);
		if (registeredWorkers.containsKey(registration.getUUID())) {
			throw new AlreadyRegisteredException();
		}
		final var record = new WorkerRecord(registration);
		record.initializeMonitor(new WorkerStateListener() {
			@Override
			public void workerUnavailable(WorkerRecord worker) {
				worker.setState(WorkerState.UNAVAILABLE);
				worker.setDeadline();
				System.err.println(String.format("Worker %s unavailable!", worker.getUUID()));
			}

			@Override
			public void reconnected(WorkerRecord worker, long ping) {
				worker.setState(WorkerState.ONLINE);
				scheduler.putWorker(record);
				System.out.println(String.format("Worker %s is online again! Ping %d ms",
						worker.getUUID(), ping));
			}

			@Override
			public void workerFailed(WorkerRecord worker) {
				worker.setState(WorkerState.OFFLINE);
				System.err.println(String.format("Worker UUID %s is offline!", worker.getUUID()));
				mainExecutorThreadPool.submit(() -> {
					handleFailedWorker(worker);
					return null;
				});
			}

			@Override
			public void isConnected(WorkerRecord worker, long ping) {
				worker.setState(WorkerState.ONLINE);
				System.out.println(String.format("Ping to %s is %d ms", worker.getUUID(), ping));
			}
		}, Configuration.SERVER_PING_INTERVAL);

		registeredWorkers.put(record.getUUID(), record);
		scheduler.putWorker(record);
		System.out.println(String.format("Worker %s is online!", record.getUUID()));
	}

	@Override
	public void register(UUID id, IClientServer clientHandle) {
		final var client = registeredClients.get(id);
		if (client != null) {
			client.online = true;
			client.handle.set(clientHandle);
		} else {
			final var record = new ClientRecord(id, clientHandle);
			registeredClients.put(id, record);
		}
		System.out.println(String.format("Client %s is online!", id));
	}

	@Override
	public void unregister(UUID id) throws RemoteException, UnregisteredClientException {
		ClientRecord client = registeredClients.get(id);
		if (client == null) {
			throw new UnregisteredClientException();
		}
		client.online = false;
		System.out.println(String.format("Client %s is offline!", id));
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

	private Path buildJobDirectoryPath(UUID userUUID, UUID parentJob, UUID thisJob)
			throws IOException {
		allJobsLock.lock();
		try {
			final var stack = new ArrayDeque<UUID>();
			stack.push(thisJob);
			var current = parentJob;
			while (current != null) {
				stack.push(current);
				current = allJobs.get(current).parentJobUUID;
			}
			var path = CHANGE_LATER_ROOT_DIR.resolve(userUUID.toString());
			while (!stack.isEmpty()) {
				path = path.resolve(stack.pop().toString());
			}
			Files.createDirectories(path);
			return path;
		} finally {
			allJobsLock.unlock();
		}
	}

	@Override
	public FileUploadHandle registerJob(UUID userUUID) throws RemoteException,
			UnregisteredClientException, MultipleJobsException, RemoteIOException {
		ClientRecord client = registeredClients.get(userUUID);
		if (client == null) {
			throw new UnregisteredClientException();
		}
		if (client.hasRegisteredJob()) {
			throw new MultipleJobsException();
		}
		final var jobUUID = UUID.randomUUID();
		ServerMainJobRecord record = null;
		try {
			final var jobDirectory = buildJobDirectoryPath(userUUID, null, jobUUID);
			record = new ServerMainJobRecord(userUUID, jobUUID, jobDirectory);
		} catch (IOException e) {
			throw new RemoteIOException(e);
		}
		client.mainJobUUID = jobUUID;
		allJobs.put(jobUUID, record);

		final var record0 = record;
		final var token = new DownloadingToken(record.getZipFile(), 2, ChronoUnit.MINUTES);
		final var downloader = new FileDownloader(token, new DownloadingListener() {
			@Override
			public void onTransferComplete() {
				System.out.println(String.format("Job %s received.", jobUUID));
				record0.setStatus(JobStatus.READY);
				scheduler.putJob(record0);
			}

			@Override
			public void onDeadlineExceeded() {
				System.err.println(String.format("Failed to receive job from %s.", userUUID));
				allJobs.remove(record0.jobUUID);
				client.mainJobUUID = null;
				try {
					client.handle.get().notifyJobFailedToStart(jobUUID, "Time expired");
				} catch (RemoteException e1) {
					System.err.println("Failed to notify client that the transfer failed");
				}
				try {
					Files.walk(record0.mainJobDirectory).sorted(Comparator.reverseOrder())
							.map(Path::toFile).forEach(File::delete);
				} catch (IOException e) {
					System.err.println("Failed to cleanup after failed job transfer");
				}
			}
		});

		return new FileUploadHandle(downloader, token.deadline());
	}

	private void jobReadyHandle(ServerJobRecord job, WorkerRecord worker) {
		FileUploadHandle handle = null;
		try {
			handle = job.postToWorker(worker.getHandle());
		} catch (RemoteException | RemoteIOException e) {
			System.err.println("Failed to upload job, state restored...");
			scheduler.putJob(job);
			scheduler.putWorker(worker);
			return;
		}
		if (!handle.isValid()) {
			// Worker already has the job files
			job.setStatus(JobStatus.RUNNING);
			return;
		}
		worker.addAssignedJob(job.getJobUUID());
		final var uploader = new FileUploader(handle, job.getZipFile(), new UploadingListener() {
			@Override
			public void onDeadlineExceeded() {
				worker.removeAssignedJob(job.getJobUUID());
				scheduler.putJob(job);
				System.out.println(String.format("Time limit exceeded for receving %s!", job));
			}

			@Override
			public void onIOException() {
				worker.removeAssignedJob(job.getJobUUID());
				scheduler.putJob(job);
				scheduler.putWorker(worker); // Try again for other jobs
				System.err.println(String.format("IOException on %s", worker.getUUID()));
			}

			@Override
			public void onUploadComplete(long bytes) {
				job.setStatus(JobStatus.RUNNING);
			}

			@Override
			public void onFailedConnection() {
				scheduler.putJob(job);
				worker.removeAssignedJob(job.getJobUUID());
				System.err.println("Worker is not available! Try later!");
			}
		});
		uploader.start();
	}

	public static void main(String[] args) {
		try {
			@SuppressWarnings("unused")
			ServerProcess cs = new ServerProcess();
		} catch (RemoteException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void out(String[] tuple) {
		linda.out(tuple);
	}

	@Override
	public String[] in(String[] tuple) {
		linda.in(tuple);
		return tuple;
	}

	@Override
	public String[] inp(String[] tuple) {
		if (linda.inp(tuple)) {
			return tuple;
		} else {
			return null;
		}
	}

	@Override
	public String[] rd(String[] tuple) {
		linda.rd(tuple);
		return tuple;
	}

	@Override
	public String[] rdp(String[] tuple) {
		if (linda.rdp(tuple)) {
			return tuple;
		} else {
			return null;
		}
	}

	@Override
	public void eval(JobAuthenticator auth, String name, Runnable thread) throws Exception {
		final var client = registeredClients.get(auth.userUUID);
		final var jobUUID = UUID.randomUUID();
		final var jobDirectory = buildJobDirectoryPath(client.uuid, auth.jobUUID, jobUUID);
		final var record = new ServerRunnableShardJobRecord(auth.userUUID, auth.mainJobUUID,
				auth.jobUUID, jobUUID, name, thread, jobDirectory);
		client.addShard(jobUUID);
		allJobs.put(jobUUID, record);
		allJobs.get(auth.jobUUID).children.add(record);
		scheduler.putJob(record);
	}

	@Override
	public void eval(JobAuthenticator auth, String className, Object[] construct, String methodName,
			Object[] arguments) throws Exception {
		final var client = registeredClients.get(auth.userUUID);
		final var jobUUID = UUID.randomUUID();
		final var args = new JobShardArgs(className, construct, methodName, arguments);
		final var jobDirectory = buildJobDirectoryPath(client.uuid, auth.jobUUID, jobUUID);
		final var record = new ServerClassShardJobRecord(auth.userUUID, auth.mainJobUUID,
				auth.jobUUID, jobUUID, args, jobDirectory);
		client.addShard(jobUUID);
		allJobs.put(jobUUID, record);
		allJobs.get(auth.jobUUID).children.add(record);
		scheduler.putJob(record);
	}

	private JobTreeNode makeNode(ServerJobRecord record) {
		var node = new JobTreeNode();
		node.status = record.getStatus();
		node.jobUUID = record.getJobUUID();
		node.description = record.description();
		node.children = new JobTreeNode[record.children.size()];
		return node;
	}

	private JobTreeNode constructJobTree(ServerJobRecord job) {
		class Pair {
			ServerJobRecord job;
			JobTreeNode node;

			Pair(ServerJobRecord job, JobTreeNode node) {
				this.job = job;
				this.node = node;
			}
		}

		transactionLock.lock();
		JobTreeNode root = null;
		try {
			final var stack = new ArrayDeque<Pair>();

			root = makeNode(job);
			stack.push(new Pair(job, root));

			while (!stack.isEmpty()) {
				final var curr = stack.pop();

				final var children = curr.job.children;

				for (int i = children.size() - 1; i >= 0; i--) {
					var child = children.get(i);
					var node = makeNode(child);
					curr.node.children[i] = node;
					stack.push(new Pair(child, node));
				}

			}
		} finally {
			transactionLock.lock();
		}

		return root;
	}

	@Override
	public FileUploadHandle jobComplete(UUID workerUUID, UUID jobUUID) throws RemoteException {
		final var jobRecord = allJobs.get(jobUUID);
		jobRecord.setStatus(JobStatus.RECEIVING_RESULTS);
		final var workerRecord = registeredWorkers.get(workerUUID);

		final var resultPath = jobRecord.getJobDirectory().resolve("results.zip");
		final var token = new DownloadingToken(resultPath.toFile(), 2, ChronoUnit.MINUTES);

		final var downloader = new FileDownloader(token, new DownloadingListener() {
			@Override
			public void onTransferComplete() {
				System.out.println(String.format("Job result for %s received.", jobUUID));
				transactionLock.lock();

				jobRecord.setStatus(JobStatus.DONE);
				workerRecord.removeAssignedJob(jobUUID);
				// Check if the whole job is complete
				final var mainJobUUID = jobRecord.getMainJobUUID();
				final var mainJob = allJobs.get(mainJobUUID);
				boolean jobComplete = mainJob.isFullyComplete();

				try {
					final var descPath = resultPath.resolveSibling("desc.txt");
					Files.write(descPath, jobRecord.description().getBytes());
					FileOperations.addFileToZip(resultPath, "/__LINDA__DESC__.txt",
							descPath.toFile());
				} catch (IOException ignore) {
				}

				transactionLock.unlock();
				if (jobComplete) {
					mainExecutorThreadPool.submit(() -> {
						try {
							// TODO TRY A FEW TIMES IF IOException HAPPENED
							uploadResultsToClient(mainJob);
						} catch (Exception e) {
							clientsWithCompletedJobs.put(jobRecord.getClientUUID(), mainJob);
						}
					});
				}
			}

			@Override
			public void onDeadlineExceeded() {
				System.err.println(String.format("Failed to receive result job for %s.", jobUUID));
				jobRecord.setStatus(JobStatus.RESULT_RETREIVAL_FAILED);
			}
		});

		return new FileUploadHandle(downloader, token.deadline());
	}

	private void uploadResultsToClient(ServerJobRecord mainJob) throws IOException {
		transactionLock.lock();
		mainJob.getResultFilesLock().lock();
		try {
			generateCompleteJobResult(mainJob);

			final var client = registeredClients.get(mainJob.getClientUUID());
			if (client == null) {
				clientsWithCompletedJobs.put(mainJob.getClientUUID(), mainJob);
				System.err.println("CLIENT OFFLINE");
				return;
			}

			// result files were generated so it's safe to call
			uploadJobToClient(client.handle.get(), mainJob);
		} finally {
			mainJob.getResultFilesLock().unlock();
			transactionLock.unlock();
		}
	}

	private void generateCompleteJobResult(ServerJobRecord mainJob) throws IOException {
		transactionLock.lock();
		mainJob.getResultFilesLock().lock();
		try {
			final var allResultsZipPath = mainJob.getMainJobDirectory().resolve("all_results.zip");
			FileOperations.createZip(allResultsZipPath.toFile());

			final var userPath = CHANGE_LATER_ROOT_DIR.resolve(mainJob.getClientUUID().toString());

			final var stack = new ArrayDeque<ServerJobRecord>();
			stack.push(mainJob);

			while (!stack.isEmpty()) {
				final var current = stack.pop();
				final var resultsPath = current.getJobDirectory().resolve("results.zip");
				final var zipRelativePath = userPath.relativize(resultsPath);
				FileOperations.addFileToZip(allResultsZipPath, zipRelativePath.toString(),
						resultsPath.toFile());

				for (final var child : current.children) {
					stack.push(child);
				}
			}
		} finally {
			mainJob.setResultFilesGenerated();
			mainJob.getResultFilesLock().unlock();
			transactionLock.unlock();
		}
	}

	// CALL ONLY WHEN RESULT FILES ARE GENERATED
	private void uploadJobToClient(IClientServer client, ServerJobRecord mainJob)
			throws RemoteException, IOException {
		final var allResultsZipPath = mainJob.getMainJobDirectory().resolve("all_results.zip");
		mainJob.getResultFilesLock().lock();
		try {
			if (!mainJob.resultFilesGenerated()) {
				generateCompleteJobResult(mainJob);
				return;
			}
		} finally {
			mainJob.getResultFilesLock().unlock();
		}

		final var handle = client.submitCompleteJob(mainJob.getJobUUID());
		clientsWithCompletedJobs.put(mainJob.getClientUUID(), mainJob);

		final var uploader = new FileUploader(handle, allResultsZipPath.toFile(),
				new UploadingListener() {
					public void onFailedConnection() {
						clientsWithCompletedJobs.put(mainJob.getClientUUID(), mainJob);
						System.err.println("FAILED TO CONNECT");
					}

					public void onDeadlineExceeded() {
						clientsWithCompletedJobs.put(mainJob.getClientUUID(), mainJob);
						System.err.println("DEADLINE EXCEEDED");
					}

					public void onIOException() {
						clientsWithCompletedJobs.put(mainJob.getClientUUID(), mainJob);
						System.err.println("IOEXCEPTION");
					}

					public void onUploadComplete(long bytes) {
						System.out.println("UPLOAD COMPLETE");
						// TODO CLEAN STUFF UP, DELETE ALL SHIT
					}
				});
		uploader.start();
	}

	// TODO: JobStatus.FAILED NOT SET ANYWHERE YET
	// TODO: REMOTE JOBS FROM WORKER'S JOB LISTS IN CASE THEY FAIL
	// TODO: THINK OF WAYS TO HANDLE WORKERS THAT PING THE SERVER AFTER THEY DIED
	// WHEN WORKER RECEIVES UnknownUUIDException IT CAN RECONNECT TO SERVER WITH NEW
	// UUID
	private void handleFailedWorker(WorkerRecord worker) {
		// TODO: Users lists have to be updated ?
		// NO! Keep them until user requests a restart

		transactionLock.lock();

		// Release resources held by this worker record
		worker.killMonitor();
		registeredWorkers.remove(worker.getUUID());

		// All users that had a part of their job executing on a deceased worker
		final var usersToNotify = worker.getAssignedJobs().parallelStream().map(allJobs::get)
				.map(ServerJobRecord::getClientUUID).collect(Collectors.toCollection(HashSet::new));

		final var latch = new CountDownLatch(usersToNotify.size());

		// For each of them in new thread try to notify them about job failure
		for (final var userUUID : usersToNotify) {
			mainExecutorThreadPool.submit(() -> {
				try {
					final var user = registeredClients.get(userUUID);
					// If user is registered - server thinks he is online
					if (user != null) {
						// All jobs that this user had on this worker
						final var userJobs = worker.getAssignedJobs().parallelStream()
								.map(allJobs::get).filter(u -> u.getClientUUID().equals(userUUID))
								.collect(Collectors.toCollection(ArrayList::new));
						// Create a future that will try and notify user about all failed jobs
						// If this future fails user is offline
						final var future = mainExecutorThreadPool.submit(() -> {
							for (final var userJob : userJobs) {
								user.handle.get().notifyJobFailed(constructJobTree(userJob));
							}
							return null;
						});
						try {
							future.get();
							return;
						} catch (InterruptedException | ExecutionException e) {
							System.err.println(
									String.format("User %s is offline, canceling all tasks"));
						}
					}
					/*
					 * TODO: DO NOT REMOVE USER IN CASE HE LOGINS AGAIN TO ASK FOR THE STATE OF HIS
					 * JOB. HE NEEDS TO BE NOTIFIED THAT THE JOB HAS BEEN KILLED
					 */

					/*
					 * User is actually offline, notify all workers online workers to terminate
					 * their jobs. EASYER TO NOTIFY ALL WORKERS TO GET RID OF HIS JOBS, AND LET THEM
					 * HANDLE IT
					 */
					killClientAssociatedJobs(userUUID);
				} finally {
					latch.countDown();
				}
			});
		}

		mainExecutorThreadPool.submit(() -> {
			try {
				latch.await();
			} catch (InterruptedException ignore) {
			}
			transactionLock.unlock();
		});
	}

	// TODO: RETURNING WORKERS AND JOBS TO THE SCHEDULER
	/*
	 * TODO: THERE IS GOING TO BE A NEED FOR AN EXCLUSIVE LOCK OVER SOME OF THE LIST
	 * BECUASE OF CONCURRENT ACCESS
	 */

	private void killClientAssociatedJobs(UUID clientUUID) {
		final var workers = registeredWorkers.values().parallelStream()
				.filter((w) -> w.getState() == WorkerState.ONLINE)
				.collect(Collectors.toCollection(ArrayList::new));

		for (final var userWorker : workers) {
			// TODO: Cancel any job that came from this user
		}
	}

	@Override
	public void reportJobFailedToStart(UUID workerUUID, UUID jobUUID) throws RemoteException {
		System.err.println(String.format("Job %s failed to start!", jobUUID));
		mainExecutorThreadPool.submit(() -> {
			final var job = allJobs.get(jobUUID);
			if (job.failedToStartCountUp() < 5) {
				// TRY AND START JOB ON THE NEW WORKER
				scheduler.putJob(job);
			} else {
				System.err.println(String.format("Job %s failed to start 5 times!", jobUUID));
				job.setStatus(JobStatus.FAILED);
				// TODO REMOVE JOB FOR APPROPRIATE STRUCTURES
				// TODO NO!!! JOB STAYS UNTIL USER << DECIDES >>
			}

			transactionLock.lock();
			try {
				final var client = registeredClients.get(job.getClientUUID());
				// If user is registered - server thinks he is online
				if (client != null) {
					try {
						final var message = String.format("Job description:%s", job.description());
						client.handle.get().notifyJobFailedToStart(jobUUID, message);
						return;
					} catch (RemoteException e) {
						// User is offline
						System.err.println(String.format(
								"Failed to notify user %s that job %s failed to start.",
								client.uuid, jobUUID));
					}
				}
				// Client actually is offline, so kill all associated jobs
				killClientAssociatedJobs(job.getClientUUID());
			} finally {
				transactionLock.unlock();
			}

		});
	}

	@Override
	public void reportJobFailed(UUID workerUUID, UUID jobUUID) throws RemoteException {
		/*
		 * TODO: JOB NEEDS TO BE PLACED SOMEWHERE ELSE WHILE WAITING FOR RESPONSE FROM
		 * CLIENT
		 */
		scheduler.putWorker(registeredWorkers.get(workerUUID)); // Worker can be used again
		System.out.println(String.format("Job %s failed!", jobUUID));
		mainExecutorThreadPool.submit(() -> {
			transactionLock.lock();
			final var job = allJobs.get(jobUUID);
			final var clientUUID = job.getClientUUID();
			final var client = registeredClients.get(clientUUID);
			try {
				final var jobTree = constructJobTree(job);
				client.handle.get().notifyJobFailed(jobTree);
				/*
				 * TODO: LATER RECEIVE RESPONSE FROM THE CLIENT ON WHAT TO DO.
				 */
				/*
				 * CLIENT CAN RESTART THE FAILED JOB OR ABORT IT.
				 */
				/*
				 * THERE IS A BIG PROBLEM IF ONE FAILED JOB IS A SUBPART OF THE LARGER JOB THAT
				 * NEEDS TO BE HANDLED IN SOME WAY!!! BEST TO LEAVE IT TO THE CLIENT TO DECIDE,
				 * SINCE JOB TREE IS AVAILABLE
				 */
			} catch (RemoteException e) {
				System.err.println(String.format("Failed to notify user %s that job %s failed.",
						client.uuid, jobUUID));
				killClientAssociatedJobs(clientUUID);
			} finally {
				transactionLock.unlock();
			}
		});
	}

	@Override
	public ResultRequestCode requestResults(UUID userUUID) throws RemoteException {
		if (clientsWithKilledJobs.containsKey(userUUID)) {
			// TODO: SEND JOB TREE TO CLIENT

			return ResultRequestCode.FAILED;
		}
		if (clientsWithCompletedJobs.containsKey(userUUID)) {
			generalTaskThreadPool.submit(() -> {
				final var clientRecord = registeredClients.get(userUUID);
				final var jobRecord = clientsWithCompletedJobs.get(userUUID);
				try {
					uploadJobToClient(clientRecord.handle.get(), jobRecord);
				} catch (IOException e) {
					System.err.println(
							String.format("Failed to upload job to client %s", clientRecord.uuid));
				}
			});
			return ResultRequestCode.READY;
		}

		return ResultRequestCode.UNKNOWN;
	}
}

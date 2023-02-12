package rs.ac.bg.etf.kdp.utils;

import java.io.Serializable;

public enum JobStatus implements Serializable {
	REGISTERED, READY, SCHEDULED, RUNNING, RECEIVING_RESULTS, DONE, RESULT_RETREIVAL_FAILED, FAILED,
	ABORTED;
}
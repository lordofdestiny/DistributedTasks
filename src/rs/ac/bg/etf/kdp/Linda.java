package rs.ac.bg.etf.kdp;

import java.io.*;

public interface Linda extends Serializable{
    /**
     * Inserts a tuple into tuple space. Sending or receiving any strings that are
     * null is not allowed
     */
    void out(String[] tuple);

    /**
     * Fetch a tuple from tuple space. This is a blocking operation.
     * If a field in this array is set to null, then that field needs
     * to be filled. If there are multiple matching tuples, any one of them
     * is fetched.
     * After this operation the tuple no longer exists in the tuple space
     */
    void in(String[] tuple);

    /**
     * Fetch of a tuple from tuple space. This is a non-blocking operation.
     * If a field in this array is set to null, then that field needs
     * to be filled. If a matching tuple exists, true is returned. If a matching
     * tuple does not exist, false is returned. If there are multiple matching
     * tuples, any one of them is fetched.
     * After this operation the tuple no longer exists in the tuple space
     */
    boolean inp(String[] tuple);

    /**
     * Reads a tuple from tuple space. This is a blocking operation. If a
     * field inside of this array is set to null, then that field needs to be
     * filled. If multiple matching tuples exist, any one of them is fetched.
     * After this operation a tuple still exists in the tupple space
     */
    void rd(String[] tuple);

    /**
     * Reads a tuple from tuple space. This is a non-blocking operation. If a
     * field inside of this array is set to null, then that field needs to be
     * filled. If a matching tuple exists, true is returned. If a matching
     * tuple does not exist, false is returned. If multiple matching tuples exist,
     * any one of them is fetched.
     * After this operation a tuple still exists in the tupple space
     */
    boolean rdp(String[] tuple);

    /**
     * Starts a new thread on a given computer.
     */
    void eval(String name, Runnable thread);

    /**
     * Starts a new thread on a given computer. Thread is started
     * by executing a method given by the parameter methodName that
     * accepts arguments of type arguments.
     * Execution is started on an instance of the class given by the
     * name of the class className and arguments of the
     * constructor initargs
     * */
    void eval(String className, Object[] construct, String methodName, Object[] arguments);
}

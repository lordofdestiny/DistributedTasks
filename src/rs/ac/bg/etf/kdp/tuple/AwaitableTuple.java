package rs.ac.bg.etf.kdp.tuple;

import java.util.concurrent.locks.*;


/**
 * This class exists for easier waiting on a tuple with
 * a given template to appear in the tuple space.
 * Given a tuple template and a lock, it crates
 * a Condition associated with the lock and
 * offers an interface for waiting for someone to
 * set the value to the tuple. No checks are done
 * regarding the matching of the template and the set value.
 * This must be done explicitly via the "matches()" method.
 * Once the value is set, condition is signaled.
 * This object is not synchronized, so the lock on witch it
 * is waiting must be locked before calling await.
 */
public class AwaitableTuple extends Tuple {
    private final Condition condition;
    private Tuple value = null;

    public AwaitableTuple(Tuple template, Lock l) {
        super(template.fields);
        condition = l.newCondition();
    }

    public void await() {
        while (isEmpty()) {
            try {
               condition.await();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private boolean isEmpty() {
        return value == null;
    }

    public void setValue(Tuple t) {
        value = t;
        condition.signal();
    }

    public Tuple getValue() {
        return value;
    }
}

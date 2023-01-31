package rs.ac.bg.etf.kdp.linda;

import java.util.*;
import java.util.concurrent.locks.*;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.tuple.AwaitableTuple;
import rs.ac.bg.etf.kdp.tuple.Tuple;
import rs.ac.bg.etf.kdp.utils.ClassMethodInvoker;
import rs.ac.bg.etf.kdp.utils.ReadWriteList;


public class CentralizedLinda implements Linda {
    private final Lock lock = new ReentrantLock();
    private final List<Tuple> tupleSpace = new ReadWriteList<>();
    private final List<AwaitableTuple> readConditions = new ReadWriteList<>();
    private final List<AwaitableTuple> takeConditions = new ReadWriteList<>();
    private final Random rand = new Random();

    @Override
    public void out(String[] tuple) {
        final var data = new Tuple(tuple);
        lock.lock();
        try {
            tupleSpace.add(0, data);
            readConditions.parallelStream()
                    .filter(data::matches)
                    .forEach(at -> at.setValue(data));

            takeConditions.parallelStream()
                    .filter(data::matches)
                    .findAny()
                    .ifPresent(at -> at.setValue(data));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void in(String[] tuple) {
        final var template = new Tuple(tuple);
        lock.lock();
        try {
            final var result = getOrWaitOn(template, takeConditions);
            tupleSpace.remove(result);
            fill(tuple, result.toStringArray());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean inp(String[] tuple) {
        final var template = new Tuple(tuple);
        final var data = tupleSpace.parallelStream()
                .filter(t -> t.matches(template))
                .findAny();
        if (data.isPresent() && tupleSpace.remove(data.get())) {
            fill(tuple, data.get().toStringArray());
            return true;
        }
        return false;
    }

    @Override
    public void rd(String[] tuple) {
        final var template = new Tuple(tuple);
        lock.lock();
        try {
            final var result = getOrWaitOn(template, readConditions);
            fill(tuple, result.toStringArray());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean rdp(String[] tuple) {
        final var template = new Tuple(tuple);
        final var data = tupleSpace.parallelStream()
                .filter(t -> t.matches(template))
                .findAny();
        if (data.isEmpty()) return false;
        fill(tuple, data.get().toStringArray());
        return true;
    }

    private Tuple getOrWaitOn(Tuple template, List<AwaitableTuple> list) {
        return tupleSpace.parallelStream()
                .filter(tup -> tup.matches(template)).findAny()
                .orElseGet(() -> {
                    final var at = new AwaitableTuple(template, lock);
                    final var pos = rand.nextInt(list.size() + 1);
                    list.add(pos, at);
                    at.await();
                    list.remove(at);
                    return at.getValue();
                });
    }

    @Override
    public void eval(String name, Runnable thread) {
        final var job = new Thread(thread, name);
        job.start();
    }

    @Override
    public void eval(String className, Object[] construct,
                     String methodName, Object[] arguments) {
        try {
            final var job = new ClassMethodInvoker(
                    className, construct,
                    methodName, arguments);
            job.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fill(String[] template, String[] data) {
        Objects.requireNonNull(template);
        Objects.requireNonNull(template);
        for (int i = 0; i < template.length; i++) {
            if (template[i] != null) continue;
            template[i] = String.valueOf(data[i]);
        }
    }
}

package rs.ac.bg.etf.kdp.linda;

import java.io.IOException;
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

    @Override
    public void out(String[] tuple) {
        final var data = Tuple.valueOf(tuple);
        lock.lock();
        try {
            tupleSpace.add(0, data);
            for (final var at : readConditions) {
                try {
                    if (data.matches(at)) {
                        at.setValue(data.deepCopy());
                    }
                } catch (IOException ignored) {
                }
            }

            // Race condition here!!!
            takeConditions.parallelStream()
                    .filter(data::matches).findFirst()
                    .ifPresent(at -> at.setValue(data));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void in(String[] tuple) {
        final var template = Tuple.valueOf(tuple);
        lock.lock();
        try {
            final var result = tupleSpace.parallelStream()
                    .filter(t -> t.matches(template))
                    .findAny();
            if (result.isPresent()) {
                tupleSpace.remove(result.get());
                fill(tuple, result.get().toStringArray());
                return;
            }
            final var at = new AwaitableTuple(template, lock);
            final var pos = Math.random() * takeConditions.size();
            takeConditions.add((int) pos, at);
            at.await();
            final var value = at.getValue();
            tupleSpace.remove(at);
            takeConditions.remove(at);
            fill(tuple, value.toStringArray());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean inp(String[] tuple) {
        final var template = Tuple.valueOf(tuple);
        final var data = tupleSpace.parallelStream()
                .filter(t -> t.matches(template))
                .findAny();
        if (data.isEmpty()) return false;
        tupleSpace.remove(data.get());
        fill(tuple, data.get().toStringArray());
        return true;
    }

    @Override
    public void rd(String[] tuple) {
        final var template = Tuple.valueOf(tuple);
        lock.lock();
        try {
            final var result = tupleSpace.parallelStream()
                    .filter(template::matches)
                    .findAny();
            if (result.isPresent()) {
                fill(tuple, result.get().toStringArray());
                return;
            }
            final var at = new AwaitableTuple(template, lock);
            final var pos = Math.random() * readConditions.size();
            readConditions.add((int) pos, at);
            at.await();
            fill(tuple, at.getValue().toStringArray());
            readConditions.remove(at);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean rdp(String[] tuple) {
        final var template = Tuple.valueOf(tuple);
        final var data = tupleSpace.parallelStream()
                .filter(t -> t.matches(template))
                .findAny();
        if (data.isEmpty()) return false;
        fill(tuple, template.toStringArray());
        return true;
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

package rs.ac.bg.etf.kdp.linda;

import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import rs.ac.bg.etf.kdp.Linda;

public class LocalLinda implements Linda {
    final ArrayList<String[]> tupleSpace = new ArrayList<>();

    public void eval(String name, Runnable thread) {
        Thread t = new Thread(thread, name);
        t.start();
    }

    public void eval(final String className, final Object[] ctorParams,
                     final String methodName, final Object[] methodsPrams) {
        final var t = new Thread(() -> {
            try {
                final var threadClass = Class.forName(className);
                final var ctorParamTypes = Stream.of(ctorParams)
                        .map(Object::getClass).toArray(Class[]::new);
                final var constructor = threadClass.getConstructor(ctorParamTypes);
                final var runningThread = constructor.newInstance(ctorParams);

                final var methodParamTypes = Stream.of(methodsPrams)
                        .map(Object::getClass).toArray(Class[]::new);

                threadClass.getMethod(methodName, methodParamTypes)
                        .invoke(runningThread, methodsPrams);
            } catch (Exception e) {
                e.printStackTrace();
            }
        },className+"::"+methodName);
        t.start();
    }

    public synchronized void in(String[] tuple) {
        while (true) {
            final var value = tupleSpace.stream()
                    .filter(data -> equals(tuple, data))
                    .findFirst();
            if (value.isPresent()) {
                final var data = value.get();
                fill(tuple, data);
                tupleSpace.remove(data);
                return;
            }
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized boolean inp(String[] tuple) {
        final var value = tupleSpace.stream()
                .filter(data -> equals(tuple, data))
                .findFirst();

        if (value.isEmpty()) return false;
        fill(tuple, value.get());
        tupleSpace.remove(tuple);

        return true;
    }

    public synchronized void out(String[] tuple) {
        tupleSpace.add((int)(Math.random()*tupleSpace.size()), tuple);
        notifyAll();
    }

    public synchronized void rd(String[] tuple) {
        while (true) {
            final var value = tupleSpace.stream()
                    .filter(data -> equals(tuple, data))
                    .findFirst();
            if (value.isPresent()) {
                fill(tuple, value.get());
                return;
            }
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public synchronized boolean rdp(String[] tuple) {
        for (String[] data : tupleSpace) {
            if (equals(tuple, data)) {
                fill(tuple, data);
                return true;
            }
        }
        return false;
    }

    private boolean equals(String[] a, String[] b) {
        if ((a == null) || (b == null)) return false;
        if (a.length != b.length) return false;

        return IntStream.range(0, a.length)
                .filter(i->a[i]!=null)
                .allMatch(i -> a[i].equals(b[i]));
    }

    private void fill(String[] a, String[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null) {
                a[i] = String.valueOf(b[i]);
            }
        }
    }

}

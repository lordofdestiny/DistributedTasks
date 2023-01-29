package rs.ac.bg.etf.kdp.tests;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.linda.LocalLinda;

import java.util.stream.IntStream;

public class TestLinda {
    public static final int iterations = 10;
    private static final Linda ll = new LocalLinda();

    public static void main(String[] args) {
        ll.eval(TestLinda.class.getName(), new Object[]{},
                "threadA", new Object[]{});
        ll.eval(TestLinda.class.getName(), new Object[]{},
                "threadB", new Object[]{});
    }

    @SuppressWarnings("unused")
    public void threadA() {
        for (int i = 0; i < iterations; i++) {
            ll.out(new String[]{"arg", String.valueOf(i), String.valueOf(i)});
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < iterations; i++) {
            final var fmt = new String[]{"result",String.valueOf(i), null, null};
            ll.in(fmt);
            final var arg = Integer.parseInt(fmt[1]);
            final var sqs = Integer.parseInt(fmt[2]);
            final var fibo = Integer.parseInt(fmt[3]);
            System.out.printf("Sum of squares up to %d: %d\n", arg, sqs);
            System.out.printf("Fibonacci(%d)=%d\n", arg, fibo);
        }
    }
    @SuppressWarnings("unused")
    public void threadB() {
        for (int i = 0; i < iterations; i++) {
            final var fmt = new String[]{"arg", null, String.valueOf(i)};
            ll.in(fmt);
            final var n = Integer.parseInt(fmt[1]);
            final var sqs = IntStream.range(0, n + 1)
                    .reduce(0, (acc, x) -> acc + x * x);
            try {
                Thread.sleep((long) (Math.random() * 500));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            final var fibo = fibo(n);
            ll.out(new String[]{"result",String.valueOf(n), String.valueOf(sqs), String.valueOf(fibo)});
        }
    }

    private static int fibo(int n) {
        if (n <= 1) return n;
        return fibo(n - 1) + fibo(n - 2);
    }
}

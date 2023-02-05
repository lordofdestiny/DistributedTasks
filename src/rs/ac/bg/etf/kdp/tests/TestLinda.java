package rs.ac.bg.etf.kdp.tests;

import java.util.Arrays;
import java.util.stream.IntStream;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.linda.CentralizedLinda;

public class TestLinda {
	public static final int iterations = 10;
	private static final Linda ll = new CentralizedLinda();

	public static void main(String[] args) {
		Object[] ca = {};
		Object[] ma = {};
		ll.eval(TestLinda.class.getName(), ca, "threadA", ma);
		ll.eval(TestLinda.class.getName(), ca, "threadB", ma);
		ll.eval(TestLinda.class.getName(), ca, "threadC", ma);
	}

	@SuppressWarnings("unused")
	public void threadA() {
		for (int i = 0; i < iterations; i++) {
			ll.out(new String[] { "arg", String.valueOf(i), String.valueOf(i) });
		}
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		String[] output = new String[iterations];
		for (int i = 0; i < iterations; i++) {
			final var fmt = new String[] { "result", null, null, null };
			ll.in(fmt);
			final var arg = Integer.parseInt(fmt[1]);
			final var sqs = Integer.parseInt(fmt[2]);
			final var fibo = Integer.parseInt(fmt[3]);
			output[arg] = String.format("Sum of squares up to %d: %d\n" + "Fibonacci(%d)=%d\n", arg,
					sqs, arg, fibo);
		}
		Arrays.stream(output).forEach(System.out::print);
	}

	@SuppressWarnings("unused")
	public void threadB() {
		for (int i = 0; i < iterations; i++) {
			final var fmt = new String[] { "arg", null, String.valueOf(i) };
			ll.in(fmt);
			final var n = Integer.parseInt(fmt[1]);
			final var sqs = IntStream.range(0, n + 1).reduce(0, (acc, x) -> acc + x * x);
			try {
				Thread.sleep((long) (Math.random() * 500));
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			final var fibo = fibo(n);
			ll.out(new String[] { "result", String.valueOf(n), String.valueOf(sqs),
					String.valueOf(fibo) });
		}
	}

	@SuppressWarnings("unused")
	public void threadC() {
		int sqsAcc = 0;
		int fiboAcc = 0;
		for (int i = 0; i < iterations; i++) {
			final var fmt = new String[] { "result", String.valueOf(i), null, null };
			ll.rd(fmt);
			sqsAcc += Integer.parseInt(fmt[2]);
			fiboAcc += Integer.parseInt(fmt[3]);
		}
		System.out.printf("Sum of all squares: %d\nSum of all fibo nums: %d\n", sqsAcc, fiboAcc);
		System.out.printf("Actuall: %d\nActuall: %d\n", sss(), sumFiboToN());
	}

	private static int fibo(int n) {
		if (n <= 1)
			return n;
		return fibo(n - 1) + fibo(n - 2);
	}

	private static int sumFiboToN() {
		int sum = 0;
		for (int i = 0; i < iterations; i++) {
			sum += fibo(i);
		}
		return sum;
	}

	private static int sss() {
		int sum = 0;
		for (int i = 0; i < iterations; i++) {
			sum += i * (i + 1) * (2 * i + 1) / 6;
		}
		return sum;
	}
}

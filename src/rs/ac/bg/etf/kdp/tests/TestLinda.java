package rs.ac.bg.etf.kdp.tests;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import rs.ac.bg.etf.kdp.linda.Linda;
import rs.ac.bg.etf.kdp.linda.TupleSpace;

public class TestLinda {
	public static final int iterations = 10;
	private static Linda ll;
	static {
		try {
			ll = TupleSpace.getLinda();
		} catch (RemoteException | NotBoundException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public static void throwRTE() {
		throw new RuntimeException();
	}

	public static void recursive() {
		final var fmt = new String[] { "counter", null };
		ll.in(fmt);
		int val = Integer.parseInt(fmt[1]);
		System.out.println("I CALL " + val);
		if (val < 2) {
			Object[] ca = {};
			Object[] ma = {};
			ll.eval(TestLinda.class.getCanonicalName(), ca, "recursive", ma);
			ll.out(new String[] { "counter", "" + (val + 1) });
		}
	}

	public static void main(String[] args) {
		Object[] ca = {};
		Object[] ma = {};
		ll.eval(TestLinda.class.getCanonicalName(), ca, "threadA", ma);
		ll.eval(TestLinda.class.getCanonicalName(), ca, "threadB", ma);
		ll.eval(TestLinda.class.getCanonicalName(), ca, "threadC", ma);
		ll.eval("Hello", (Runnable & Serializable) () -> System.out.println("Hello world!!!"));
//		ll.eval(TestLinda.class.getCanonicalName(), ca, "throwRTE", ma);
		ll.out(new String[] { "counter", "0" });
		ll.eval(TestLinda.class.getCanonicalName(), ca, "recursive", ma);
		try {
			String hello = "Hello world";
			FileWriter fw = new FileWriter(new File("Output.txt"));
			fw.write(hello);
			fw.flush();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (args.length >= 1) {
			try (Stream<String> stream = Files.lines(new File(args[0]).toPath());
					FileWriter fw = new FileWriter(new File("Output2.txt"))) {
				stream.forEach((line) -> {
					try {
						fw.write(line);
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
				for (final var arg : args) {
					System.out.println(arg);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	@SuppressWarnings("unused")
	public static void threadA() {
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
	public static void threadB() {
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
	public static void threadC() {
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

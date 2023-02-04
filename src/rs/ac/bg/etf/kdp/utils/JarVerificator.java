package rs.ac.bg.etf.kdp.utils;

import java.io.File;
import java.util.jar.JarFile;

public class JarVerificator {
	public static boolean isValidJar(File file) {
		try (JarFile jarFile = new JarFile(file)) {
			final var e = jarFile.entries();
			while (e.hasMoreElements()) {
				e.nextElement();
			}
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	public static boolean hasClass(File file, String name, boolean checkJar) {
		if (checkJar && !isValidJar(file))
			return false;
		try (JarFile jarFile = new JarFile(file)) {
			final var e = jarFile.entries();
			while (e.hasMoreElements()) {
				final var fileName = e.nextElement().getName();
				if (!fileName.endsWith(".class")) {
					continue;
				}
				if (fileName.replace("/", ".").replace(".class", "").equals(name)) {
					return true;
				}
			}
		} catch (Exception ex) {
			return false;
		}
		return false;
	}

	public static boolean hasClass(File file, String name) {
		return hasClass(file, name, false);
	}
}

package rs.ac.bg.etf.kdp.utils;

import java.io.File;

public class PropertyLoader {
    public static void loadConfiguration() throws Exception {
        final var cf = new File("rs/ac/bg/etf/kdp/system.properties");
        final var url = ClassLoader.getSystemResource(cf.getPath());
        System.getProperties().load(url.openStream());
    }
}

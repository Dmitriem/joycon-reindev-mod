package com.example.joyconmod;

import java.io.*;
import java.nio.file.*;

public class NativeExtractor {
    public static File extractResource(String resourcePath, String outName) throws IOException {
        InputStream is = NativeExtractor.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }
        Path tmp = Files.createTempDirectory("joycon-native-");
        File out = tmp.resolve(outName).toFile();
        try (OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
        }
        out.deleteOnExit();
        return out;
    }
}

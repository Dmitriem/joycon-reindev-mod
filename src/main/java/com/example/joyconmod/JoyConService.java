package com.example.joyconmod;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class JoyConService {
    private Process proc;
    private volatile State state = new State();
    private Thread reader;

    public static class State {
        // axis values: raw [-32767 .. 32767]
        public volatile int leftAx = 0, leftAy = 0;
        public volatile int rightAx = 0, rightAy = 0;
        public final Map<String, Boolean> leftButtons = new ConcurrentHashMap<>();
        public final Map<String, Boolean> rightButtons = new ConcurrentHashMap<>();
    }

    public State getState() { return state; }

    public void startProcess() {
        if (proc != null && proc.isAlive()) return;
        try {
            // извлекаем/загружаем бинарник
            File bin = NativeExtractor.extractResource("/native/linux-aarch64/joycond", "joycond");
            bin.setExecutable(true, false);

            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
            pb.redirectErrorStream(true);
            proc = pb.start();

            reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        parseLine(line.trim());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "JoyConStdoutReader");
            reader.setDaemon(true);
            reader.start();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static final Pattern PAT = Pattern.compile("^([LR])\\s+([A-Z0-9_]+)\\s+(-?\\d+)$");
    private void parseLine(String line) {
        // форматы: "L ABS_X -105" или "R BTN_SOUTH 1"
        // или "L BTN_TL 0"
        Matcher m = PAT.matcher(line);
        if (m.matches()) {
            String side = m.group(1);
            String code = m.group(2);
            int val = Integer.parseInt(m.group(3));
            boolean pressed = val != 0;
            if (side.equals("L")) {
                switch (code) {
                    case "ABS_X": state.leftAx = val; break;
                    case "ABS_Y": state.leftAy = val; break;
                    default: state.leftButtons.put(code, pressed); break;
                }
            } else {
                switch (code) {
                    case "ABS_RX": state.rightAx = val; break;
                    case "ABS_RY": state.rightAy = val; break;
                    default: state.rightButtons.put(code, pressed); break;
                }
            }
        } else {
            // ignore / log optionally
            // System.out.println("JOY: unknown line: " + line);
        }
    }
}

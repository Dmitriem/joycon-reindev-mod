package com.example.joyconmod;

import java.io.*;
import java.util.concurrent.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.src.*;

public class JoyConMod {
    private static final JoyConService svc = new JoyConService();
    private static volatile boolean started = false;

    static {
        // Попытка запуститься при загрузке класса — FoxLoader/мод загрузит jar, и это сработает.
        try {
            start();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void start() {
        if (started) return;
        started = true;

        // Запустить нативный демона и парсер stdout
        svc.startProcess();

        // Тик-обновление: создаём фоновый поток, который каждую ~50ms применяет состояние контроллера в Minecraft
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "JoyCon-Mod-Tick");
            t.setDaemon(true);
            return t;
        });

        exec.scheduleAtFixedRate(() -> {
            try {
                applyToMinecraft();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private static void applyToMinecraft() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || !mc.isGamePaused()) {
            // читаем состояние и применяем к KeyBinding и к игроку
        }
        JoyConService.State s = svc.getState();

        // --- Движение (левый стиков: ABS_X / ABS_Y) -> WASD ---
        int dead = 1500; // на основе файлов, твои устройства имеют flat ~1500 (см. твой файл)
        float lx = s.leftAx / 32767f;
        float ly = s.leftAy / 32767f;

        boolean forward = ly < -0.25f;
        boolean back    = ly > 0.25f;
        boolean left    = lx < -0.25f;
        boolean right   = lx > 0.25f;

        // Key codes для WASD (стандартные: W=17, A=30, S=31, D=32 в старых keycodes — но лучше взять из KeyBinding array)
        // Мы используем существующие биндинги из Minecraft
        try {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), forward);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), back);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), left);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), right);
        } catch (Throwable t) {
            // fallback: ничего
        }

        // --- Взгляд (правый стиков) ---
        float rx = s.rightAx / 32767f;
        float ry = s.rightAy / 32767f;

        // скорость поворота на тик (подгоняй)
        float yawSpeed = rx * 6.0f;   // масштаб
        float pitchSpeed = ry * 4.0f;

        if (mc.thePlayer != null) {
            mc.thePlayer.rotationYaw += yawSpeed * 0.05f;
            mc.thePlayer.rotationPitch += pitchSpeed * 0.02f;
            // Ограничим pitch
            if (mc.thePlayer.rotationPitch > 90) mc.thePlayer.rotationPitch = 90;
            if (mc.thePlayer.rotationPitch < -90) mc.thePlayer.rotationPitch = -90;
        }

        // --- Кнопки (примерное маппирование) ---
        // Левый Joy-Con: BTN_Z -> BTN_TL -> BTN_TL2 -> Select etc.
        // Правый Joy-Con: BTN_SOUTH/EAST/NORTH/WEST and TR/TR2/START/MODE etc.

        // Map to in-game actions:
        // BTN_SOUTH  -> Use/RightClick (keyBindUseItem)
        // BTN_NORTH  -> Inventory? (keyBindInventory)
        // BTN_EAST   -> Drop? (Q) [обычно]
        // BTN_TR / BTN_TR2 -> Sprint or Sneak (shift) (example)
        // BTN_THUMB L/R ->? (we can map to crouch)

        // We'll check raw booleans and set appropriate keybinds:
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), s.rightButtons.getOrDefault("BTN_SOUTH", false));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), s.rightButtons.getOrDefault("BTN_EAST", false));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindInventory.getKeyCode(), s.rightButtons.getOrDefault("BTN_NORTH", false));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), s.leftButtons.getOrDefault("BTN_TL", false) || s.rightButtons.getOrDefault("BTN_TR", false));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), s.leftButtons.getOrDefault("BTN_TL2", false) || s.rightButtons.getOrDefault("BTN_TR2", false));
    }
}

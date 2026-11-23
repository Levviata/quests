/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibdebug.neo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("reflibdebug")
public class ReflibDebugNeo {

    private static final Logger LOGGER = LoggerFactory.getLogger("reflibdebug");

    private static final class Job {
        final String line;
        final boolean atEnd;
        final CompletableFuture<String> result = new CompletableFuture<>();

        Job(String line, boolean atEnd) {
            this.line = line;
            this.atEnd = atEnd;
        }
    }

    private final Queue<Job> startQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Job> endQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> later = new ConcurrentLinkedQueue<>();
    private String token = "";

    public ReflibDebugNeo() {
        NeoForge.EVENT_BUS.register(this);
        try {
            byte[] raw = new byte[16];
            new SecureRandom().nextBytes(raw);
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            token = sb.toString();
            ServerSocket server = null;
            int port = 47100;
            for (; port < 47200 && server == null; port++) {
                try {
                    server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
                } catch (IOException busy) {
                    server = null;
                }
            }
            if (server == null) {
                LOGGER.error("No free port in 47100-47199; control port disabled");
                return;
            }
            final ServerSocket ss = server;
            Path gameDir = FMLPaths.GAMEDIR.get();
            Files.writeString(gameDir.resolve("reflibdebug.port"), (port - 1) + "\n" + token + "\n", StandardCharsets.UTF_8);
            Thread t = new Thread(() -> serve(ss), "reflibdebug-server");
            t.setDaemon(true);
            t.start();
            LOGGER.info("Control port open on 127.0.0.1:{} (details in reflibdebug.port)", port - 1);
        } catch (Throwable ex) {
            LOGGER.error("Control port not started", ex);
        }
    }

    private void serve(ServerSocket ss) {
        while (true) {
            try {
                Socket s = ss.accept();
                Thread h = new Thread(() -> handle(s), "reflibdebug-conn");
                h.setDaemon(true);
                h.start();
            } catch (IOException ex) {
                LOGGER.error("accept failed", ex);
                return;
            }
        }
    }

    private void handle(Socket s) {
        try (s; BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = s.getOutputStream()) {
            s.setSoTimeout(30000);
            String line = in.readLine();
            String reply;
            if (line == null) {
                return;
            }
            int sp = line.indexOf(' ');
            if (sp < 0 || !line.substring(0, sp).equals(token)) {
                return;
            }
            String cmd = line.substring(sp + 1);
            String name = cmd.contains(" ") ? cmd.substring(0, cmd.indexOf(' ')) : cmd;
            if (name.equals("ping")) {
                reply = "pong";
            } else {
                Job job = new Job(cmd, name.equals("shot"));
                (job.atEnd ? endQueue : startQueue).add(job);
                try {
                    reply = job.result.get(20, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    reply = "error: " + ex;
                }
            }
            out.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {

        }
    }

    @SubscribeEvent
    public void onTickStart(ClientTickEvent.Pre event) {
        Runnable r;
        while ((r = later.poll()) != null) {
            try {
                r.run();
            } catch (Throwable ex) {
                LOGGER.error("delayed action failed", ex);
            }
        }
        Job j;
        while ((j = startQueue.poll()) != null) {
            run(j);
        }
    }

    @SubscribeEvent
    public void onFrameEnd(RenderFrameEvent.Post event) {
        Job j;
        while ((j = endQueue.poll()) != null) {
            run(j);
        }
    }

    private void run(Job j) {
        try {
            String r = execute(j);
            if (r != null) {
                j.result.complete(r);
            }
        } catch (Throwable ex) {
            LOGGER.error("command failed: {}", j.line, ex);
            j.result.complete("error: " + ex);
        }
    }

    private String execute(Job j) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        String[] a = j.line.split(" ", 2);
        String cmd = a[0].toLowerCase(Locale.ROOT);
        String rest = a.length > 1 ? a[1] : "";
        long win = mc.getWindow().handle();
        switch (cmd) {
            case "status": {
                StringBuilder sb = new StringBuilder();
                sb.append("window ").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight())
                        .append(" gui_scale ").append(mc.getWindow().getGuiScale());
                sb.append(" screen ").append(mc.screen == null ? "none" : mc.screen.getClass().getName());
                sb.append(" world ").append(mc.level != null);
                if (mc.player != null) {
                    sb.append(" player ").append(mc.player.getName().getString()).append(" at ").append((int) mc.player.getX()).append(' ')
                            .append((int) mc.player.getY()).append(' ').append((int) mc.player.getZ());
                }
                return sb.toString();
            }
            case "gui": {
                if (mc.screen == null) {
                    return "no screen";
                }
                return mc.screen.getClass().getName() + " title=\"" + mc.screen.getTitle().getString() + "\" size " + mc.screen.width + "x" + mc.screen.height;
            }
            case "shot": {
                File f = new File(rest.trim());
                if (f.getParentFile() != null) {
                    f.getParentFile().mkdirs();
                }
                Screenshot.takeScreenshot(mc.getMainRenderTarget(), (NativeImage img) -> {
                    try (img) {
                        img.writeToFile(f);
                        j.result.complete("saved " + f + " " + img.getWidth() + "x" + img.getHeight());
                    } catch (IOException ex) {
                        j.result.complete("error: " + ex);
                    }
                });
                return null;
            }
            case "move":
                return mouseMove(mc, win, rest);
            case "click":
            case "down":
            case "up": {
                String[] p = rest.trim().split("\\s+");
                if (p.length >= 2 && !p[0].isEmpty()) {
                    mouseMoveTo(win, Double.parseDouble(p[0]), Double.parseDouble(p[1]));
                }
                int btn = p.length >= 3 ? Integer.parseInt(p[2]) : 0;
                if (cmd.equals("down")) {
                    mouseButton(mc, win, btn, 1);
                } else if (cmd.equals("up")) {
                    mouseButton(mc, win, btn, 0);
                } else {
                    mouseButton(mc, win, btn, 1);
                    later.add(() -> mouseButton(mc, win, btn, 0));
                }
                return "ok";
            }
            case "wheel": {
                double n = Double.parseDouble(rest.trim());
                Method m = Minecraft.getInstance().mouseHandler.getClass().getDeclaredMethod("onScroll", long.class, double.class, double.class);
                m.setAccessible(true);
                m.invoke(mc.mouseHandler, win, 0.0, n);
                return "ok";
            }
            case "key":
            case "keyctrl":
            case "keyshift": {
                int code = keyCode(rest.trim());
                if (code < 0) {
                    return "error: unknown key " + rest;
                }
                int mods = cmd.equals("keyctrl") ? 2 : cmd.equals("keyshift") ? 1 : 0;
                key(mc, win, code, 1, mods);
                later.add(() -> key(mc, win, code, 0, mods));
                return "ok";
            }
            case "hold": {
                int code = keyCode(rest.trim());
                key(mc, win, code, 1, 0);
                return "ok";
            }
            case "release": {
                int code = keyCode(rest.trim());
                key(mc, win, code, 0, 0);
                return "ok";
            }
            case "type": {
                Method m = mc.keyboardHandler.getClass().getDeclaredMethod("charTyped", long.class, CharacterEvent.class);
                m.setAccessible(true);
                rest.codePoints().forEach(cp -> {
                    try {
                        m.invoke(mc.keyboardHandler, win, new CharacterEvent(cp));
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                return "ok";
            }
            case "chat": {
                if (mc.player == null) {
                    return "error: no world";
                }
                if (rest.startsWith("/")) {
                    mc.player.connection.sendCommand(rest.substring(1));
                } else {
                    mc.player.connection.sendChat(rest);
                }
                return "ok";
            }
            case "buttons": {
                if (mc.screen == null) {
                    return "no screen";
                }
                StringBuilder sb = new StringBuilder();
                for (AbstractWidget w : widgets(mc)) {
                    sb.append(w.getMessage().getString()).append(" @ ").append(centre(mc, w)).append(w.isActive() ? "" : " (inactive)").append('\n');
                }
                return sb.toString().trim();
            }
            case "pressbutton": {
                for (AbstractWidget w : widgets(mc)) {
                    if (w.getMessage().getString().toLowerCase(Locale.ROOT).contains(rest.trim().toLowerCase(Locale.ROOT)) && w.visible) {
                        double[] c = centreXY(mc, w);
                        mouseMoveTo(win, c[0], c[1]);
                        mouseButton(mc, win, 0, 1);
                        later.add(() -> mouseButton(mc, win, 0, 0));
                        return "pressed " + w.getMessage().getString();
                    }
                }
                return "error: no such button";
            }
            case "worlds": {
                File saves = new File(mc.gameDirectory, "saves");
                StringBuilder sb = new StringBuilder();
                File[] dirs = saves.listFiles(File::isDirectory);
                if (dirs != null) {
                    for (File d : dirs) {
                        if (new File(d, "level.dat").exists()) {
                            sb.append(d.getName()).append('\n');
                        }
                    }
                }
                return sb.toString().trim();
            }
            case "openworld": {
                String folder = rest.trim();
                mc.createWorldOpenFlows().openWorld(folder, () -> LOGGER.warn("openWorld cancelled or failed: {}", folder));
                return "opening " + folder;
            }
            case "resize": {
                String[] p = rest.trim().split("\\s+");
                mc.getWindow().setWindowed(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                return "ok";
            }
            default:
                return "error: unknown command " + cmd;
        }
    }

    private String mouseMove(Minecraft mc, long win, String rest) throws Exception {
        String[] p = rest.trim().split("\\s+");
        mouseMoveTo(win, Double.parseDouble(p[0]), Double.parseDouble(p[1]));
        return "ok";
    }

    private void mouseMoveTo(long win, double x, double y) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Method m = mc.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
        m.setAccessible(true);
        m.invoke(mc.mouseHandler, win, x, y);
    }

    private void mouseButton(Minecraft mc, long win, int button, int action) {
        try {
            Method m = mc.mouseHandler.getClass().getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
            m.setAccessible(true);
            m.invoke(mc.mouseHandler, win, new MouseButtonInfo(button, 0), action);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void key(Minecraft mc, long win, int code, int action, int mods) {
        try {
            Method m = mc.keyboardHandler.getClass().getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
            m.setAccessible(true);
            m.invoke(mc.keyboardHandler, win, action, new KeyEvent(code, 0, mods));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int keyCode(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (n.length() == 1) {
            char c = n.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return c;
            }
        }
        switch (n) {
            case "SPACE": return 32;
            case "ESC": case "ESCAPE": return 256;
            case "ENTER": case "RETURN": return 257;
            case "TAB": return 258;
            case "BACKSPACE": return 259;
            case "DELETE": return 261;
            case "RIGHT": return 262;
            case "LEFT": return 263;
            case "DOWN": return 264;
            case "UP": return 265;
            case "PAGEUP": return 266;
            case "PAGEDOWN": return 267;
            case "HOME": return 268;
            case "END": return 269;
            case "LSHIFT": return 340;
            case "LCTRL": return 341;
            case "LALT": return 342;
            case "SLASH": return 47;
            default:
                if (n.matches("F([1-9]|1[0-2])")) {
                    return 289 + Integer.parseInt(n.substring(1));
                }
                return -1;
        }
    }

    private List<AbstractWidget> widgets(Minecraft mc) {
        List<AbstractWidget> out = new ArrayList<>();
        collect(mc.screen.children(), out);
        return out;
    }

    private void collect(List<? extends GuiEventListener> in, List<AbstractWidget> out) {
        for (GuiEventListener l : in) {
            if (l instanceof AbstractWidget w) {
                out.add(w);
            }
            if (l instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c) {
                collect(c.children(), out);
            }
        }
    }

    private double[] centreXY(Minecraft mc, AbstractWidget w) {
        double scale = mc.getWindow().getGuiScale();
        return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
    }

    private String centre(Minecraft mc, AbstractWidget w) {
        double[] c = centreXY(mc, w);
        return (int) c[0] + " " + (int) c[1];
    }
}

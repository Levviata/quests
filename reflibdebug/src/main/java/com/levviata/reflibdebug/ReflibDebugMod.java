/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibdebug;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldSummary;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Development tool, not for players. Opens a control port on 127.0.0.1 (never any other address) so a developer can
 * script the client from outside while its window stays in the background: screenshots straight from the game's own
 * framebuffer, mouse and key input delivered to the current screen, button presses, opening a world, chat commands.
 *
 * <p>The port and a random token are written to {@code reflibdebug.port} in the game folder (line 1 = port, line 2 =
 * token). Every request is one line "token command arguments"; the reply is text. Requests from anything that does not
 * know the token are dropped. There is deliberately no command to quit the game or to run arbitrary code.
 */
@Mod(modid = ReflibDebugMod.ID, name = "Reflib Debug Control", version = ReflibDebugMod.VERSION,
        clientSideOnly = true, acceptableRemoteVersions = "*")
public class ReflibDebugMod {

    public static final String ID = "reflibdebug";
    public static final String VERSION = "0.1.0";
    private static final Logger LOGGER = LogManager.getLogger(ID);

    /** A request waiting for the client thread. {@code atEnd}: run after the frame is drawn (screenshots). */
    private static final class Task {
        final String line;
        final boolean atEnd;
        final CompletableFuture<String> result = new CompletableFuture<>();

        Task(String line, boolean atEnd) {
            this.line = line;
            this.atEnd = atEnd;
        }
    }

    private final Queue<Task> startQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Task> endQueue = new ConcurrentLinkedQueue<>();
    private String token = "";

    // client-thread state
    private boolean fake;          // pretend the mouse is at (fakeX, fakeY) in framebuffer pixels, top-left origin
    private int fakeX;
    private int fakeY;
    private int heldButton = -1;   // mouse button currently held by the script
    private int wheelPending;
    private final Set<Integer> heldKeys = new HashSet<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
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
            File gameDir = event.getModConfigurationDirectory().getParentFile();
            Files.write(new File(gameDir, "reflibdebug.port").toPath(),
                    ((port - 1) + "\n" + token + "\n").getBytes(StandardCharsets.UTF_8));
            Thread t = new Thread(() -> serve(ss), "reflibdebug-server");
            t.setDaemon(true);
            t.start();
            LOGGER.info("Control port open on 127.0.0.1:{} (details in {})", port - 1, "reflibdebug.port");
        } catch (Throwable ex) {
            LOGGER.error("Control port not started", ex);
        }
    }

    // ------------------------------------------------------------------------------ socket thread

    private void serve(ServerSocket ss) {
        while (true) {
            try {
                Socket s = ss.accept();
                Thread h = new Thread(() -> handle(s), "reflibdebug-conn");
                h.setDaemon(true);
                h.start();
            } catch (IOException ex) {
                return;
            }
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            OutputStream out = sock.getOutputStream();
            String reply;
            if (line == null || !line.startsWith(token + " ")) {
                return; // not our client: say nothing
            }
            String cmd = line.substring(token.length() + 1).trim();
            String verb = cmd.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            Task task = new Task(cmd, verb.equals("shot"));
            (task.atEnd ? endQueue : startQueue).add(task);
            try {
                reply = task.result.get(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
                reply = "error: " + ex;
            }
            out.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // client went away
        }
    }

    // ------------------------------------------------------------------------------ client thread

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase == TickEvent.Phase.START) {
            mc.gameSettings.pauseOnLostFocus = false; // a background window must not pop the pause menu
            applyFakeInput(mc);
            Task t;
            while ((t = startQueue.poll()) != null) {
                run(mc, t);
            }
        } else {
            Task t;
            while ((t = endQueue.poll()) != null) {
                run(mc, t);
            }
        }
    }

    private void run(Minecraft mc, Task t) {
        try {
            t.result.complete(execute(mc, t.line));
        } catch (Throwable ex) {
            t.result.complete("error: " + ex);
        }
    }

    /** Keeps the scripted mouse position, wheel and held keys in place for this frame. */
    private void applyFakeInput(Minecraft mc) {
        if (fake) {
            int y = mc.displayHeight - fakeY - 1;
            for (String name : new String[] {"x", "event_x", "absolute_x", "latestX"}) {
                setStatic(Mouse.class, name, fakeX);
            }
            for (String name : new String[] {"y", "event_y", "absolute_y", "latestY"}) {
                setStatic(Mouse.class, name, y);
            }
            GuiScreen screen = mc.currentScreen;
            if (screen != null && heldButton >= 0) {
                int gx = guiX(mc, fakeX);
                int gy = guiY(mc, fakeY);
                invoke(screen, new String[] {"mouseClickMove", "func_146273_a"}, new Class<?>[] {int.class, int.class, int.class, long.class},
                        gx, gy, heldButton, 0L);
            }
        }
        if (wheelPending != 0) {
            int amount = wheelPending;
            wheelPending = 0;
            setStatic(Mouse.class, "dwheel", amount);
            setStatic(Mouse.class, "event_dwheel", amount);
            setStatic(Mouse.class, "eventButton", -1);
            GuiScreen screen = mc.currentScreen;
            if (screen != null) {
                // no real event exists, so hand the wheel event to the screen the way the game loop would
                try {
                    invoke(screen, new String[] {"handleMouseInput", "func_146274_d"}, new Class<?>[0]);
                } catch (RuntimeException ignored) {
                    // the screen did not take it
                }
            }
            setStatic(Mouse.class, "event_dwheel", 0);
        }
        for (int code : heldKeys) {
            setKeyDown(code, true);
        }
    }

    private String execute(Minecraft mc, String cmd) throws Exception {
        String[] a = cmd.split("\\s+");
        String verb = a[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "ping":
                return "pong " + VERSION;
            case "status":
                return status(mc);
            case "shot":
                return shot(mc, cmd.substring(4).trim());
            case "move": // pixels of the screenshot, top-left origin
                fake = true;
                fakeX = Integer.parseInt(a[1]);
                fakeY = Integer.parseInt(a[2]);
                return "ok";
            case "unmove":
                fake = false;
                heldButton = -1;
                return "ok";
            case "down":
            case "up":
            case "click": {
                int x = Integer.parseInt(a[1]);
                int y = Integer.parseInt(a[2]);
                int btn = a.length > 3 ? Integer.parseInt(a[3]) : 0;
                fake = true;
                fakeX = x;
                fakeY = y;
                GuiScreen screen = mc.currentScreen;
                if (screen == null) {
                    return "no screen open";
                }
                int gx = guiX(mc, x);
                int gy = guiY(mc, y);
                if (!verb.equals("up")) {
                    invoke(screen, new String[] {"mouseClicked", "func_73864_a"}, new Class<?>[] {int.class, int.class, int.class}, gx, gy, btn);
                    heldButton = btn;
                }
                if (!verb.equals("down")) {
                    GuiScreen again = mc.currentScreen; // the click may have opened another screen
                    if (again != null) {
                        invoke(again, new String[] {"mouseReleased", "func_146286_b"}, new Class<?>[] {int.class, int.class, int.class}, gx, gy, btn);
                    }
                    heldButton = -1;
                }
                return "ok gui=" + gx + "," + gy;
            }
            case "wheel":
                wheelPending = Integer.parseInt(a[1]) * 120;
                return "ok";
            case "key": // key <name or lwjgl code> [char]  e.g. key ESCAPE, key F, key 1
                return key(mc, a, false);
            case "keyctrl":
                return key(mc, a, true);
            case "hold":
                heldKeys.add(code(a[1]));
                return "ok";
            case "release": {
                int c = code(a[1]);
                heldKeys.remove(c);
                setKeyDown(c, false);
                return "ok";
            }
            case "type": {
                GuiScreen screen = mc.currentScreen;
                if (screen == null) {
                    return "no screen open";
                }
                String text = cmd.length() > 5 ? cmd.substring(5) : "";
                for (char ch : text.toCharArray()) {
                    int c = Keyboard.getKeyIndex(String.valueOf(Character.toUpperCase(ch)));
                    invoke(screen, new String[] {"keyTyped", "func_73869_a"}, new Class<?>[] {char.class, int.class}, ch, c);
                    if (mc.currentScreen != screen) {
                        break;
                    }
                }
                return "ok";
            }
            case "press": { // a key binding while no screen is open (e.g. the book key)
                int c = code(a[1]);
                KeyBinding.setKeyBindState(c, true);
                KeyBinding.onTick(c);
                MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent()); // mods that listen for key events
                KeyBinding.setKeyBindState(c, false);
                return "ok";
            }
            case "buttons":
                return buttons(mc);
            case "pressbutton":
                return pressButton(mc, cmd.substring(11).trim());
            case "chat": {
                if (mc.player == null) {
                    return "no world";
                }
                String text = cmd.substring(5);
                // client-side commands (such as /rqbook) are normally intercepted by the chat screen, not by the player
                if (text.startsWith("/") && ClientCommandHandler.instance.executeCommand(mc.player, text) != 0) {
                    return "ok client command";
                }
                mc.player.sendChatMessage(text);
                return "ok";
            }
            case "worlds": {
                ISaveFormat fmt = mc.getSaveLoader();
                StringBuilder sb = new StringBuilder();
                for (WorldSummary ws : fmt.getSaveList()) {
                    sb.append(ws.getFileName()).append(" | ").append(ws.getDisplayName()).append('\n');
                }
                return sb.toString().trim();
            }
            case "openworld": {
                if (mc.world != null) {
                    return "a world is already open";
                }
                String folder = cmd.substring(10).trim();
                mc.launchIntegratedServer(folder, folder, null);
                return "ok launching " + folder;
            }
            case "resize": { // resize w h: change the window size (pixels)
                int w = Integer.parseInt(a[1]);
                int h = Integer.parseInt(a[2]);
                org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(w, h));
                mc.resize(w, h);
                return "ok " + w + "x" + h;
            }
            case "gui": { // close the current screen: back to the world
                mc.displayGuiScreen(null);
                return "ok";
            }
            default:
                return "error: unknown command " + verb;
        }
    }

    private String key(Minecraft mc, String[] a, boolean ctrl) {
        int c = code(a[1]);
        char ch = a.length > 2 ? a[2].charAt(0) : (a[1].length() == 1 ? a[1].toLowerCase(Locale.ROOT).charAt(0) : '\0');
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            if (c == Keyboard.KEY_ESCAPE) {
                mc.displayInGameMenu();
                return "ok pause menu";
            }
            return "no screen open (use press for key bindings)";
        }
        if (ctrl) {
            setKeyDown(Keyboard.KEY_LCONTROL, true);
        }
        try {
            invoke(screen, new String[] {"keyTyped", "func_73869_a"}, new Class<?>[] {char.class, int.class}, ch, c);
        } finally {
            if (ctrl) {
                setKeyDown(Keyboard.KEY_LCONTROL, false);
            }
        }
        return "ok";
    }

    private static int code(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            int c = Keyboard.getKeyIndex(s.toUpperCase(Locale.ROOT));
            if (c == 0) {
                throw new IllegalArgumentException("unknown key " + s);
            }
            return c;
        }
    }

    // ------------------------------------------------------------------------------ queries

    private String status(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        GuiScreen screen = mc.currentScreen;
        ScaledResolution sr = new ScaledResolution(mc);
        sb.append("screen=").append(screen == null ? "none" : screen.getClass().getName()).append('\n');
        sb.append("display=").append(mc.displayWidth).append('x').append(mc.displayHeight)
                .append(" gui=").append(sr.getScaledWidth()).append('x').append(sr.getScaledHeight())
                .append(" scale=").append(sr.getScaleFactor()).append('\n');
        sb.append("world=").append(mc.world != null).append(" player=").append(mc.player != null).append('\n');
        sb.append("fakeMouse=").append(fake ? fakeX + "," + fakeY : "off").append(" heldButton=").append(heldButton).append('\n');
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String buttons(Minecraft mc) {
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            return "no screen open";
        }
        List<GuiButton> list = buttonList(screen);
        ScaledResolution sr = new ScaledResolution(mc);
        double k = (double) mc.displayWidth / sr.getScaledWidth();
        StringBuilder sb = new StringBuilder();
        for (GuiButton b : list) {
            sb.append(b.id).append(" | ").append(b.displayString).append(" | px ")
                    .append((int) ((b.x + b.width / 2.0) * k)).append(',').append((int) ((b.y + b.height / 2.0) * k))
                    .append(b.enabled ? "" : " (disabled)").append('\n');
        }
        return sb.toString().trim();
    }

    private String pressButton(Minecraft mc, String text) {
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            return "no screen open";
        }
        for (GuiButton b : buttonList(screen)) {
            if (b.displayString != null && b.displayString.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)) && b.enabled) {
                invoke(screen, new String[] {"actionPerformed", "func_146284_a"}, new Class<?>[] {GuiButton.class}, b);
                return "ok pressed " + b.displayString;
            }
        }
        return "no enabled button containing '" + text + "'";
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttonList(GuiScreen screen) {
        Object o = getField(screen, new String[] {"buttonList", "field_146292_n"});
        return o instanceof List ? (List<GuiButton>) o : new ArrayList<GuiButton>();
    }

    private String shot(Minecraft mc, String path) throws IOException {
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        BufferedImage img = ScreenShotHelper.createScreenshot(w, h, mc.getFramebuffer());
        File f = new File(path);
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(img, "png", f);
        return "ok " + w + "x" + h + " " + f.getAbsolutePath();
    }

    // ------------------------------------------------------------------------------ helpers

    private static int guiX(Minecraft mc, int px) {
        ScaledResolution sr = new ScaledResolution(mc);
        return px * sr.getScaledWidth() / mc.displayWidth;
    }

    private static int guiY(Minecraft mc, int py) {
        ScaledResolution sr = new ScaledResolution(mc);
        return py * sr.getScaledHeight() / mc.displayHeight;
    }

    private static void setKeyDown(int code, boolean down) {
        try {
            Field f = Keyboard.class.getDeclaredField("keyDownBuffer");
            f.setAccessible(true);
            ByteBuffer buf = (ByteBuffer) f.get(null);
            if (buf != null && code >= 0 && code < buf.capacity()) {
                buf.put(code, (byte) (down ? 1 : 0));
            }
        } catch (Throwable ignored) {
            // the key just will not look held
        }
    }

    private static void setStatic(Class<?> c, String name, int value) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            f.setInt(null, value);
        } catch (Throwable ignored) {
            // field absent in this LWJGL build
        }
    }

    private static Object getField(Object o, String[] names) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (String n : names) {
                try {
                    Field f = c.getDeclaredField(n);
                    f.setAccessible(true);
                    return f.get(o);
                } catch (Throwable ignored) {
                    // try next
                }
            }
        }
        return null;
    }

    private static void invoke(Object o, String[] names, Class<?>[] types, Object... args) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (String n : names) {
                try {
                    Method m = c.getDeclaredMethod(n, types);
                    m.setAccessible(true);
                    m.invoke(o, args);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // try next name or class
                } catch (Throwable ex) {
                    throw new RuntimeException(ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        }
        throw new IllegalStateException("method " + names[0] + " not found on " + o.getClass().getName());
    }
}

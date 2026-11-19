/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;
import com.levviata.reflib.gui.ViewTransform;
import com.levviata.reflib.gui.Widget;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.ProgressEngine;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.model.QuestRules;
import com.levviata.reflibquests.model.TeamProgress;
import com.levviata.reflibquests.net.QuestNet;

/**
 * The read-mostly quest book: chapter list on the left, a pannable and zoomable map in the middle and a detail
 * panel for the selected quest on the right. Actions (claim, check, hand in) are sent to the server, which
 * decides; the screen only shows the synced state. Placeholder art only: coloured squares and item icons.
 * Limitations: all quests are drawn as squares, chapter images and quest links are not shown, and the detail
 * panel does not scroll.
 */
@SideOnly(Side.CLIENT)
final class QuestBookWidget extends Widget {

    private static final int LIST_W = 120;
    private static final int DETAIL_W = 230;
    private static final double GRID = 26.0;
    private static final double NODE = 22.0;
    /** How much of its square an icon fills; the icon scales with the square at every zoom level. */
    private static final float ICON_FRACTION = 0.72f;

    // state survives screen rebuilds (window resize) and re-opening
    private static String chapterId = "";
    private static String selectedQuest = "";
    private static int stateVersion = -1;
    private static final Map<String, ViewTransform> VIEWS = new HashMap<>();
    private static int listScroll;

    private record Hit(Rect rect, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private boolean pressedOnMap;
    private boolean moved;
    private double movedDistance;
    private int lastRawX;
    private int lastRawY;

    QuestBookWidget(Rect bounds) {
        super(bounds);
    }

    // ------------------------------------------------------------------ helpers

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private Rect listRect() {
        return new Rect(bounds.x(), bounds.y(), LIST_W, bounds.height());
    }

    private Rect mapRect() {
        return new Rect(bounds.x() + LIST_W + 4, bounds.y(), bounds.width() - LIST_W - 4, bounds.height());
    }

    private Rect detailRect() {
        Rect m = mapRect();
        return new Rect(m.x() + m.width() - DETAIL_W, m.y(), DETAIL_W, m.height());
    }

    private boolean detailOpen() {
        return !selectedQuest.isEmpty() && ClientQuests.book() != null && ClientQuests.book().quest(selectedQuest) != null;
    }

    private static List<Chapter> visibleChapters(QuestBook book) {
        List<Chapter> out = new ArrayList<>();
        for (Chapter c : book.chapters()) {
            if (!c.alwaysInvisible) {
                out.add(c);
            }
        }
        return out;
    }

    private static void scissor(Rect r) {
        ScaledResolution res = new ScaledResolution(mc());
        int f = res.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(r.x() * f, mc().displayHeight - (r.y() + r.height()) * f, Math.max(0, r.width() * f), Math.max(0, r.height() * f));
    }

    private static void unscissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void rect(int x, int y, int w, int h, int argb) {
        Gui.drawRect(x, y, x + w, y + h, argb);
    }

    private ViewTransform viewFor(Chapter c, Rect map) {
        return VIEWS.computeIfAbsent(c.id, id -> {
            ViewTransform v = new ViewTransform(0.3, 3.0);
            if (!c.quests.isEmpty()) {
                double minX = Double.MAX_VALUE;
                double minY = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE;
                double maxY = -Double.MAX_VALUE;
                for (Quest q : c.quests) {
                    minX = Math.min(minX, q.x);
                    maxX = Math.max(maxX, q.x);
                    minY = Math.min(minY, q.y);
                    maxY = Math.max(maxY, q.y);
                }
                v.centerOn((minX + maxX) / 2 * GRID, (minY + maxY) / 2 * GRID, map.x() + map.width() / 2.0, map.y() + map.height() / 2.0);
            } else {
                v.centerOn(0, 0, map.x() + map.width() / 2.0, map.y() + map.height() / 2.0);
            }
            return v;
        });
    }

    private enum State { DONE, LOCKED, ACTIVE, AVAILABLE }

    private static State stateOf(QuestBook book, ProgressEngine engine, TeamProgress p, Quest q) {
        if (p.isCompleted(q.id)) {
            return State.DONE;
        }
        if (engine.isLocked(p, q)) {
            return State.LOCKED;
        }
        return p.isStarted(q.id) ? State.ACTIVE : State.AVAILABLE;
    }

    private static int colorOf(State s) {
        switch (s) {
            case DONE:
                return 0xFF2E8B3A;
            case LOCKED:
                return 0xFF555560;
            case ACTIVE:
                return 0xFFC9A227;
            default:
                return 0xFF3A6EA5;
        }
    }

    private Chapter currentChapter(QuestBook book) {
        Chapter c = book.chapter(chapterId);
        return c != null && !c.alwaysInvisible ? c : null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawSelf(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        FontRenderer fr = mc().fontRenderer;
        Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xF0181820);
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        ProgressEngine engine = ClientQuests.engine();
        if (book == null || p == null || engine == null) {
            fr.drawStringWithShadow("Waiting for the quest book from the server...", bounds.x() + 10, bounds.y() + 10, 0xFFFFFF);
            return;
        }
        if (stateVersion != ClientQuests.bookVersion()) {
            stateVersion = ClientQuests.bookVersion();
            VIEWS.clear();
            chapterId = "";
            selectedQuest = "";
            listScroll = 0;
        }
        List<Chapter> chapters = visibleChapters(book);
        if (currentChapter(book) == null) {
            chapterId = chapters.isEmpty() ? "" : chapters.get(0).id;
        }
        drawChapterList(fr, book, p, chapters, mouseX, mouseY);
        Chapter chapter = currentChapter(book);
        Rect map = mapRect();
        Gui.drawRect(map.x(), map.y(), map.x() + map.width(), map.y() + map.height(), 0xFF101016);
        if (chapter == null) {
            fr.drawStringWithShadow("This quest book has no chapters.", map.x() + 10, map.y() + 10, 0xAAAAAA);
            return;
        }
        // the map only uses the space the detail panel leaves free, so nothing is drawn underneath it
        Rect visibleMap = detailOpen() ? new Rect(map.x(), map.y(), map.width() - DETAIL_W, map.height()) : map;
        applyDragFromMouse();
        scissor(visibleMap);
        drawMap(fr, book, engine, p, chapter, map);
        unscissor();
        if (detailOpen()) {
            // item icons write depth values; clear them so later 2D drawing is never hidden behind an icon
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            scissor(detailRect());
            drawDetail(fr, book, engine, p);
            unscissor();
        }
    }

    /**
     * Pans while the button is held, from the mouse position read once per rendered frame, in real pixels.
     * Mouse <em>events</em> are only handled once per game tick (20 per second), which made dragging choppy.
     */
    private void applyDragFromMouse() {
        if (!pressedOnMap) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            return; // the release event ends the press
        }
        double factor = new ScaledResolution(mc()).getScaleFactor();
        int rawX = Mouse.getX();
        int rawY = Mouse.getY();
        double dx = (rawX - lastRawX) / factor;
        double dy = (lastRawY - rawY) / factor;
        lastRawX = rawX;
        lastRawY = rawY;
        if (dx == 0 && dy == 0) {
            return;
        }
        QuestBook book = ClientQuests.book();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter != null) {
            viewFor(chapter, mapRect()).panBy(dx, dy);
        }
        movedDistance += Math.abs(dx) + Math.abs(dy);
        if (movedDistance > 4) {
            moved = true;
        }
    }

    private void drawChapterList(FontRenderer fr, QuestBook book, TeamProgress p, List<Chapter> chapters, int mouseX, int mouseY) {
        Rect r = listRect();
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF22222E);
        scissor(r);
        int y = r.y() + 6 - listScroll;
        for (Chapter c : chapters) {
            int done = 0;
            int total = 0;
            for (Quest q : c.quests) {
                if (QuestRules.isVisible(book, p, q)) {
                    total++;
                    if (p.isCompleted(q.id)) {
                        done++;
                    }
                }
            }
            Rect row = new Rect(r.x() + 2, y, r.width() - 4, 16);
            boolean selected = c.id.equals(chapterId);
            boolean hover = row.contains(mouseX, mouseY);
            Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), selected ? 0xFF3A4A6A : hover ? 0xFF2E2E3E : 0x00000000);
            String title = fr.trimStringToWidth(c.title.isEmpty() ? c.id : c.title, r.width() - 40);
            fr.drawStringWithShadow(title, row.x() + 4, row.y() + 4, selected ? 0xFFFFFF : 0xCCCCCC);
            String pct = total == 0 ? "-" : done + "/" + total;
            fr.drawStringWithShadow(pct, row.x() + row.width() - fr.getStringWidth(pct) - 3, row.y() + 4, done == total && total > 0 ? 0x55FF55 : 0x888888);
            final String id = c.id;
            hits.add(new Hit(row, () -> {
                chapterId = id;
                selectedQuest = "";
            }));
            y += 18;
        }
        unscissor();
    }

    private void drawMap(FontRenderer fr, QuestBook book, ProgressEngine engine, TeamProgress p, Chapter chapter, Rect map) {
        ViewTransform v = viewFor(chapter, map);
        double zoom = v.zoom();
        // dependency lines first, then nodes on top
        for (Quest q : chapter.quests) {
            double sx = v.screenX(q.x * GRID);
            double sy = v.screenY(q.y * GRID);
            if (q.dependencies.isEmpty() || !QuestRules.isVisible(book, p, q)) {
                continue;
            }
            for (String depId : q.dependencies) {
                Quest dep = book.quest(depId);
                if (dep == null || book.chapterOf(depId) != chapter || !QuestRules.isVisible(book, p, dep)) {
                    continue;
                }
                double dx = v.screenX(dep.x * GRID);
                double dy = v.screenY(dep.y * GRID);
                if (Math.max(sx, dx) < map.x() || Math.min(sx, dx) > map.x() + map.width()
                        || Math.max(sy, dy) < map.y() || Math.min(sy, dy) > map.y() + map.height()) {
                    continue;
                }
                int color = p.isCompleted(depId) ? 0xFF3C9A4A : 0xFF666672;
                int t = Math.max(1, (int) Math.round(zoom * 1.5));
                line(dx, dy, sx, dy, t, color); // horizontal from the dependency
                line(sx, dy, sx, sy, t, color); // then vertical into the quest
            }
        }
        for (Quest q : chapter.quests) {
            double size = NODE * q.size * zoom;
            double sx = v.screenX(q.x * GRID) - size / 2;
            double sy = v.screenY(q.y * GRID) - size / 2;
            if (sx + size < map.x() || sx > map.x() + map.width() || sy + size < map.y() || sy > map.y() + map.height()
                    || !QuestRules.isVisible(book, p, q)) {
                continue;
            }
            State state = stateOf(book, engine, p, q);
            int x = (int) Math.round(sx);
            int y = (int) Math.round(sy);
            int s = Math.max(4, (int) Math.round(size));
            boolean selected = q.id.equals(selectedQuest);
            String shape = QuestArt.shapeName(q);
            if (QuestArt.hasShape(shape)) {
                // art from the player's own FTB Quests jar: the shape tinted by state, with its outline on top
                QuestArt.quad(QuestArt.shapeTexture(shape, "background"), x, y, s, s, colorOf(state));
                QuestArt.quad(QuestArt.shapeTexture(shape, "outline"), x, y, s, s, selected ? 0xFFFFFFFF : 0xFF20202A);
                if (q.optional) {
                    rect(x + s / 4, y, s / 2, 2, 0xFF9999AA);
                }
            } else {
                rect(x - 1, y - 1, s + 2, s + 2, selected ? 0xFFFFFFFF : 0xFF000000);
                rect(x, y, s, s, colorOf(state));
                if (q.optional) {
                    rect(x, y, s, 2, 0xFF9999AA);
                }
            }
            // icons keep one fixed size at every zoom level; they are simply left out when the node is too small
            ItemStack icon = QuestText.iconOf(q);
            if (state == State.LOCKED && s >= 14 && QuestArt.available() && QuestArt.has("textures/gui/quest_locked.png")) {
                int lock = Math.max(6, s / 3);
                QuestArt.quad(QuestArt.guiTexture("quest_locked"), x + s - lock - 1, y + 1, lock, lock, 0xFFFFFFFF);
            }
            if (icon != null && s >= 6) {
                // the icon is always the same fraction of its square, so it grows and shrinks with the zoom
                float scale = s * ICON_FRACTION / 16f;
                float offset = (s - 16f * scale) / 2f;
                drawItem(icon, x + offset, y + offset, scale);
            }
        }
    }

    private static void line(double x1, double y1, double x2, double y2, int thickness, int color) {
        int ax = (int) Math.round(Math.min(x1, x2));
        int bx = (int) Math.round(Math.max(x1, x2));
        int ay = (int) Math.round(Math.min(y1, y2));
        int by = (int) Math.round(Math.max(y1, y2));
        int h = thickness / 2;
        Gui.drawRect(ax - h, ay - h, bx + thickness - h, by + thickness - h, color);
    }

    private static void drawItem(ItemStack stack, float x, float y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, scale);
        RenderHelper.enableGUIStandardItemLighting();
        mc().getRenderItem().zLevel = 100f;
        mc().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        mc().getRenderItem().zLevel = 0f;
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ detail panel

    private void drawDetail(FontRenderer fr, QuestBook book, ProgressEngine engine, TeamProgress p) {
        Quest q = book.quest(selectedQuest);
        Rect r = detailRect();
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xF2101018);
        Gui.drawRect(r.x(), r.y(), r.x() + 1, r.y() + r.height(), 0xFF444455);
        int x = r.x() + 8;
        int w = r.width() - 16;
        int y = r.y() + 8;
        boolean details = QuestRules.detailsVisible(book, p, q);
        State state = stateOf(book, engine, p, q);
        fr.drawStringWithShadow(fr.trimStringToWidth(QuestText.title(q), w), x, y, 0xFFD060);
        y += 12;
        if (!q.subtitle.isEmpty() && details) {
            fr.drawStringWithShadow(fr.trimStringToWidth(q.subtitle, w), x, y, 0xAAAAAA);
            y += 12;
        }
        String stateText = state == State.DONE ? "Completed" : state == State.LOCKED ? "Locked: finish the quests before it"
                : state == State.ACTIVE ? "In progress " + QuestRules.percent(p, q) + "%" : "Available";
        fr.drawStringWithShadow(stateText, x, y, colorOf(state) | 0xFF000000);
        y += 14;
        if (!details) {
            fr.drawStringWithShadow("Details are hidden until this quest is available.", x, y, 0x888888);
            return;
        }
        for (String line : q.description) {
            for (String wrapped : fr.listFormattedStringToWidth(line, w)) {
                fr.drawString(wrapped, x, y, 0xDDDDDD);
                y += 10;
            }
        }
        if (!q.description.isEmpty()) {
            y += 4;
        }
        fr.drawStringWithShadow("Tasks", x, y, 0xFFFFFF);
        y += 12;
        for (Task t : q.tasks) {
            boolean done = QuestRules.taskDone(p, t);
            String text = QuestText.task(t) + (t.optionalTask() ? " (optional)" : "");
            for (String wrapped : fr.listFormattedStringToWidth(text, w - 4)) {
                fr.drawString(wrapped, x + 4, y, done ? 0x55FF55 : 0xDDDDDD);
                y += 10;
            }
            if (t.maxProgress() > 1) {
                long have = Math.min(p.taskProgress(t.id()), t.maxProgress());
                int barW = w - 60;
                rect(x + 4, y, barW, 5, 0xFF33333D);
                rect(x + 4, y, (int) (barW * have / (double) t.maxProgress()), 5, 0xFF3C9A4A);
                fr.drawString(have + "/" + t.maxProgress(), x + 8 + barW, y - 2, 0xAAAAAA);
                y += 9;
            }
            if (!done && !p.isCompleted(q.id) && QuestRules.canProgress(book, p, q)) {
                if (t.type().equals("checkmark")) {
                    y = button(fr, x + 4, y, "Check", () -> QuestNet.check(t.id()));
                } else if (t.type().equals("item") && consumes(book, t)) {
                    y = button(fr, x + 4, y, "Hand in items", () -> QuestNet.submit(t.id()));
                }
            }
            y += 3;
        }
        y += 4;
        fr.drawStringWithShadow("Rewards", x, y, 0xFFFFFF);
        y += 12;
        UUID me = mc().player == null ? null : mc().player.getUniqueID();
        for (Reward rw : q.rewards) {
            boolean claimed = me != null && p.isClaimed(rw.id(), me);
            boolean ready = me != null && engine.canClaim(p, me, rw);
            String text = QuestText.reward(rw) + (claimed ? " (claimed)" : "");
            for (String wrapped : fr.listFormattedStringToWidth(text, w - 4)) {
                fr.drawString(wrapped, x + 4, y, claimed ? 0x777777 : 0xDDDDDD);
                y += 10;
            }
            if (ready && rw.type().equals("choice")) {
                RewardTable table = book.tables().get(QuestText.tableId(rw));
                if (table != null) {
                    int i = 0;
                    for (WeightedReward wr : table.entries) {
                        final int index = i++;
                        y = button(fr, x + 8, y, "Take: " + QuestText.reward(wr.reward()), () -> QuestNet.claim(rw.id(), index));
                    }
                }
            } else if (ready) {
                y = button(fr, x + 4, y, "Claim", () -> QuestNet.claim(rw.id(), -1));
            }
            y += 3;
        }
    }

    private static boolean consumes(QuestBook book, Task t) {
        return t.params().has("consume_items") ? t.params().getBool("consume_items", false)
                : book.settings.getBool("default_consume_items", false);
    }

    private int button(FontRenderer fr, int x, int y, String label, Runnable action) {
        int w = fr.getStringWidth(label) + 10;
        Rect r = new Rect(x, y, w, 12);
        boolean hover = r.contains(lastMouseX, lastMouseY);
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), hover ? 0xFF6B6B8F : 0xFF4A4A68);
        fr.drawStringWithShadow(label, x + 5, y + 2, 0xFFFFFF);
        hits.add(new Hit(r, action));
        return y + 15;
    }

    private int lastMouseX;
    private int lastMouseY;

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.draw(mouseX, mouseY, partialTicks);
    }

    // ------------------------------------------------------------------ input

    private Quest questAt(int mx, int my) {
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter == null || p == null) {
            return null;
        }
        Rect map = mapRect();
        if (!map.contains(mx, my) || detailOpen() && detailRect().contains(mx, my)) {
            return null;
        }
        ViewTransform v = viewFor(chapter, map);
        for (int i = chapter.quests.size() - 1; i >= 0; i--) {
            Quest q = chapter.quests.get(i);
            double size = NODE * q.size * v.zoom();
            double sx = v.screenX(q.x * GRID) - size / 2;
            double sy = v.screenY(q.y * GRID) - size / 2;
            if (mx >= sx && mx < sx + Math.max(4, size) && my >= sy && my < sy + Math.max(4, size) && QuestRules.isVisible(book, p, q)) {
                return q;
            }
        }
        return null;
    }

    @Override
    protected boolean onClick(int mouseX, int mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.rect().contains(mouseX, mouseY)) {
                if (button == 0) {
                    h.action().run();
                }
                return true;
            }
        }
        if (mapRect().contains(mouseX, mouseY) && !(detailOpen() && detailRect().contains(mouseX, mouseY))) {
            pressedOnMap = true;
            moved = false;
            movedDistance = 0;
            lastRawX = Mouse.getX();
            lastRawY = Mouse.getY();
            return true;
        }
        return detailOpen() && detailRect().contains(mouseX, mouseY) || listRect().contains(mouseX, mouseY);
    }

    @Override
    protected boolean onDrag(int mouseX, int mouseY, int button, int dx, int dy) {
        return pressedOnMap; // panning is done per frame in applyDragFromMouse(), not per tick here
    }

    @Override
    protected boolean onRelease(int mouseX, int mouseY, int button) {
        if (!pressedOnMap) {
            return false;
        }
        pressedOnMap = false;
        if (!moved) {
            Quest q = questAt(mouseX, mouseY);
            selectedQuest = q == null ? "" : q.id;
        }
        return true;
    }

    @Override
    protected boolean onScroll(int mouseX, int mouseY, int delta) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return false;
        }
        if (listRect().contains(mouseX, mouseY)) {
            int max = Math.max(0, visibleChapters(book).size() * 18 - listRect().height() + 12);
            listScroll = Math.max(0, Math.min(max, listScroll - Integer.signum(delta) * 18));
            return true;
        }
        Chapter chapter = currentChapter(book);
        if (chapter != null && mapRect().contains(mouseX, mouseY)) {
            viewFor(chapter, mapRect()).zoomAt(mouseX, mouseY, delta > 0 ? 1.15 : 1 / 1.15);
            return true;
        }
        return false;
    }

    @Override
    protected List<String> tooltip(int mouseX, int mouseY) {
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        ProgressEngine engine = ClientQuests.engine();
        if (book == null || p == null || engine == null || pressedOnMap && moved) {
            return null;
        }
        Quest q = questAt(mouseX, mouseY);
        if (q == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        boolean details = QuestRules.detailsVisible(book, p, q);
        lines.add("§e" + (details ? QuestText.title(q) : "???"));
        State s = stateOf(book, engine, p, q);
        lines.add(s == State.DONE ? "§aCompleted" : s == State.LOCKED ? "§8Locked" : "§7" + QuestRules.percent(p, q) + "%");
        return lines;
    }

    @Override
    protected boolean onKey(char typed, int keyCode) {
        return false;
    }
}

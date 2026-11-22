/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import com.levviata.reflibquests.text.RichText;
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

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;
import com.levviata.reflib.gui.ViewTransform;
import com.levviata.reflib.gui.Widget;
import com.levviata.reflibquests.model.Definitions;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.ProgressEngine;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Tristate;
import com.levviata.reflibquests.model.QuestRules;
import com.levviata.reflibquests.model.QuestSearch;
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

    private static final int LIST_W = 99;
    private static final int DETAIL_W = 205;
    private static final int HEADER = 22;
    private static final double GRID = 26.0;
    /** Chapter pictures were drawn about 12% too large next to the quests in side-by-side checks. */
    private static final double IMAGE_SCALE = 0.88;
    private static final double NODE = 22.0;
    /** How much of its square an icon fills; the icon scales with the square at every zoom level. */
    private static final float ICON_FRACTION = 0.72f;

    // state survives screen rebuilds (window resize) and re-opening
    private static String chapterId = "";
    private static String selectedQuest = "";
    /** Quests picked with Ctrl + click (edit mode), and the ones last copied. */
    private static final java.util.Set<String> SELECTION = new java.util.LinkedHashSet<>();
    private static final List<String> COPIED = new ArrayList<>();
    private static String selectionChapter = "";
    /** Ctrl + R: lines through the grid spot under the mouse, out to the edges of the map. */
    private static boolean crosshairs;
    /** Quests viewed, oldest first; Backspace goes back through them. */
    private static final List<String> VIEWED = new ArrayList<>();
    private static String viewedTop = "";

    /** The picked quests belong to one chapter: switching chapters drops them. */
    private static void syncSelection(Chapter chapter) {
        if (!chapter.id.equals(selectionChapter)) {
            SELECTION.clear();
            selectionChapter = chapter.id;
        }
    }
    private static int stateVersion = -1;
    private static final Map<String, ViewTransform> VIEWS = new HashMap<>();
    private static int listScroll;
    private static boolean listPinned;
    private static double listAnim;
    private static long listLastNanos;
    private static int listContentHeight;
    private static Rect listBar;
    private static int listBarThumb;
    private static boolean draggingListBar;

    private void scrollListTo(int mouseY) {
        if (listBar == null) {
            return;
        }
        int travel = Math.max(1, listBar.height() - listBarThumb);
        double f = Math.max(0, Math.min(1, (mouseY - listBar.y() - listBarThumb / 2.0) / travel));
        listScroll = (int) Math.round(f * Math.max(0, listContentHeight - listBar.height()));
    }
    private static String lastListChapter = "";
    private static final java.util.Set<String> EXPANDED_GROUPS = new java.util.HashSet<>();
    private static boolean searching;
    private static final TextEditor QUERY = new TextEditor("", 48);
    private static String cachedQuery = null;
    private static int cachedBookVersion = -1;
    private static TeamProgress cachedProgress;
    private static List<QuestSearch.Result> results = List.of();

    // ---- editing (only usable when the server says this player may edit)
    private static boolean editing;
    private static String linkFrom = "";
    private static boolean confirmDelete;
    private static boolean confirmDeleteChapter;
    private static int detailScroll;
    private static int descPage;
    private static int detailContentHeight;
    private static String lastDetailQuest = "";
    private static final String[] SHAPE_CYCLE = {"", "circle", "square", "diamond", "hexagon", "octagon", "pentagon", "heart", "gear", "rsquare"};
    /** The quest being moved (a middle click or the menu starts it) and where it would land, in grid units. */
    private Quest movingQuest;
    private double ghostX;
    private double ghostY;

    /** The quests that travel with the one being moved (when it is one of several picked ones). */
    private final List<Quest> moveGroup = new ArrayList<>();

    private void startQuestMove(Quest q) {
        playClick();
        movingQuest = q;
        ghostX = q.x;
        ghostY = q.y;
        pressedOnMap = false;
        moveGroup.clear();
        QuestBook book = ClientQuests.book();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter != null && SELECTION.contains(q.id) && SELECTION.size() > 1) {
            for (Quest other : chapter.quests) {
                if (SELECTION.contains(other.id) && other != q) {
                    moveGroup.add(other);
                }
            }
        }
    }

    /** Drop the quest at the highlighted place, or leave it where it was. */
    private void endQuestMove(boolean keep) {
        Quest q = movingQuest;
        movingQuest = null;
        List<Quest> group = new ArrayList<>(moveGroup);
        moveGroup.clear();
        if (q == null) {
            return;
        }
        if (keep) {
            playClick(); // the quest is dropped
        }
        if (keep && (ghostX != q.x || ghostY != q.y)) {
            double dx = ghostX - q.x;
            double dy = ghostY - q.y;
            List<DataNode> moves = new ArrayList<>();
            for (Quest other : group) {
                other.x += dx;
                other.y += dy;
                moves.add(DataNode.Compound.empty().with("id", other.id).with("x", other.x).with("y", other.y));
            }
            q.x = ghostX;
            q.y = ghostY;
            if (group.isEmpty()) {
                send("move_quest", args("id", q.id, "x", q.x, "y", q.y));
            } else {
                moves.add(DataNode.Compound.empty().with("id", q.id).with("x", q.x).with("y", q.y));
                send("move_quests", DataNode.Compound.empty().with("moves", new DataNode.Lst(moves))); // one step in the history
            }
            SELECTION.clear(); // the quests are unpicked once they have landed
        }
    }

    /** A box being dragged with the middle button to pick quests: where it started, in screen pixels. */
    private boolean boxing;
    private int boxButton = 2;
    private int boxX0;
    private int boxY0;

    private Rect clip;

    private record Hit(Rect rect, Runnable action, String tip, Runnable right) {
        Hit(Rect rect, Runnable action, String tip) {
            this(rect, action, tip, null);
        }
    }

    private record MenuItem(String label, String shortcut, String icon, boolean separator, Runnable action) {
        MenuItem(String label, Runnable action) {
            this(label, "", "", false, action);
        }
    }

    private static List<MenuItem> menu = List.of();
    private static int menuX;
    private static int menuY;
    private static boolean propsOpen;
    private static int propsScroll;
    private static int propsContentHeight;
    private static String lastPropsQuest = "";

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

    /** The chapter panel floats over the left edge of the map; it can be folded away with the arrow tab. */
    private int listWidth() {
        return (int) Math.round(listFull * listAnim);
    }

    /** The panel is as wide as its longest chapter name (FTB Quests puts no limit on it), never narrower than the base width. */
    private int listFull = LIST_W;

    private void updateListFull(FontRenderer fr, QuestBook book) {
        int need = LIST_W;
        for (Chapter c : visibleChapters(book)) {
            boolean inGroup = !c.groupId.isEmpty() && book.groups().stream().anyMatch(g -> g.id.equals(c.groupId));
            if (inGroup && !EXPANDED_GROUPS.contains(c.groupId)) {
                continue; // a chapter folded away inside a closed group takes no width
            }
            String name = RichText.stripCodes(c.title.isEmpty() ? c.id : c.title);
            // measured on FTB Quests 26.1.2: the name starts 15 units in (22 in a group) and 14 units of room follow it, 20 with an alert badge
            need = Math.max(need, 15 + (inGroup ? 7 : 0) + fr.getStringWidth(name) + (CHAPTER_ALERT.getOrDefault(c.id, false) ? 20 : 14));
        }
        for (Definitions.ChapterGroup g : book.groups()) {
            Chapter parent = g.raw.getString("chapter", "").isEmpty() ? null : book.chapter(g.raw.getString("chapter", ""));
            String gname = RichText.stripCodes(parent != null ? parent.title : g.title.isEmpty() ? g.id : g.title);
            need = Math.max(need, 14 + fr.getStringWidth(gname) + 22); // the group's own row, with its + button
        }
        listFull = Math.min(need, Math.max(LIST_W, bounds.width() - 60)); // the map keeps some room
    }

    private Rect listRect() {
        return new Rect(bounds.x(), bounds.y(), listWidth(), bounds.height());
    }

    /** The map fills the whole screen, like the upstream questbook. */
    private Rect mapRect() {
        return bounds;
    }

    /** Normal mode: a small window centred over the map. Edit mode keeps a tall side panel for the many controls. */
    private Rect propsRect() {
        return new Rect(bounds.x() + bounds.width() - DETAIL_W, bounds.y(), DETAIL_W, bounds.height());
    }

    private boolean propsOpen() {
        return isEditing() && propsOpen && detailOpen();
    }

    private Rect popupRect;
    private static long autoPinNoticeUntil;

    private boolean insideFocusWindow(int mx, int my) {
        return detailRect().contains(mx, my) || propsOpen() && propsRect().contains(mx, my) || popupRect != null && popupRect.contains(mx, my);
    }

    private boolean overPanel(int mx, int my) {
        return detailOpen() && detailRect().contains(mx, my) || propsOpen() && propsRect().contains(mx, my);
    }

    private Rect detailRect() {
        int h = Math.max(70, Math.min(bounds.height() - 40, detailContentHeight));
        int cx = bounds.x() + bounds.width() / 2;
        QuestBook rb = ClientQuests.book();
        Chapter shownIn = rb == null || selectedQuest.isEmpty() ? null : rb.chapterOf(selectedQuest);
        int w = shownIn == null ? DETAIL_W : Math.min(bounds.width() - 20, Math.max(DETAIL_W, shownIn.minWindowWidth)); // the chapter's minimum width
        return new Rect(cx - w / 2, bounds.y() + (bounds.height() - h) / 2, w, h);
    }

    private boolean detailOpen() {
        return !selectedQuest.isEmpty() && ClientQuests.book() != null && ClientQuests.book().quest(selectedQuest) != null;
    }

    private static List<Chapter> visibleChapters(QuestBook book) {
        List<Chapter> out = new ArrayList<>();
        for (Chapter c : book.chapters()) {
            if (!c.alwaysInvisible || isEditing()) {
                out.add(c); // an invisible chapter is listed while editing
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

    /** One zoom level for every chapter: zooming in one chapter zooms them all. */
    private static double sharedZoom = 1.0;

    /** Where the middle of the map was when each chapter's view was made or last looked at, to follow a changing window size. */
    private static final Map<String, double[]> VIEW_MIDDLE = new HashMap<>();

    private ViewTransform viewFor(Chapter c, Rect map) {
        ViewTransform view = viewForUnsynced(c, map);
        // the window can be another size than when the view was made (the game starts small and is then enlarged): keep what the player sees in the middle
        double midX = map.x() + map.width() / 2.0;
        double midY = map.y() + map.height() / 2.0;
        double[] before = VIEW_MIDDLE.put(c.id, new double[] {midX, midY});
        if (before != null && (before[0] != midX || before[1] != midY)) {
            view.panBy(midX - before[0], midY - before[1]);
        }
        if (view.zoom() != sharedZoom) {
            view.zoomAt(map.x() + map.width() / 2.0, map.y() + map.height() / 2.0, sharedZoom / view.zoom());
        }
        return view;
    }

    private ViewTransform viewForUnsynced(Chapter c, Rect map) {
        return VIEWS.computeIfAbsent(c.id, id -> {
            ViewTransform v = new ViewTransform(0.3, 3.0);
            v.zoomAt(0, 0, sharedZoom / v.zoom());
            Quest focus = c.autofocusId.isEmpty() ? null : c.quests.stream().filter(fq -> fq.id.equals(c.autofocusId)).findFirst().orElse(null);
            if (focus != null) {
                v.centerOn(focus.x * GRID, focus.y * GRID, map.x() + map.width() / 2.0, map.y() + map.height() / 2.0);
            } else if (!c.quests.isEmpty()) {
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
        return c != null && (!c.alwaysInvisible || isEditing()) ? c : null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawSelf(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        FontRenderer fr = mc().fontRenderer;
        QuestArt.background(bounds);
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        ProgressEngine engine = ClientQuests.engine();
        if (book == null || p == null || engine == null) {
            fr.drawStringWithShadow("Waiting for the quest book from the server...", bounds.x() + 10, bounds.y() + 10, 0xFFFFFF);
            return;
        }
        if (stateVersion != ClientQuests.bookVersion()) {
            boolean fresh = stateVersion < 0 || ClientQuests.bookVersion() < stateVersion;
            stateVersion = ClientQuests.bookVersion();
            if (fresh) {
                VIEWS.clear();
                VIEW_MIDDLE.clear();
                chapterId = "";
                selectedQuest = "";
                listScroll = 0;
            } else {
                // an edit or a reload changed the book: stay in the same chapter, on the same quest, with the same camera
                if (book.chapter(chapterId) == null) {
                    chapterId = "";
                }
                if (book.quest(selectedQuest) == null) {
                    selectedQuest = "";
                }
                VIEWS.keySet().removeIf(id -> book.chapter(id) == null);
            }
        }
        List<Chapter> chapters = visibleChapters(book);
        if (currentChapter(book) == null) {
            chapterId = chapters.isEmpty() ? "" : chapters.get(0).id;
        }
        Chapter chapter = currentChapter(book);
        if (!selectedQuest.isEmpty() && !selectedQuest.equals(viewedTop)) {
            viewedTop = selectedQuest; // every quest opened goes on the Backspace list
            VIEWED.add(selectedQuest);
            if (VIEWED.size() > 500) {
                VIEWED.remove(0);
            }
        }
        Rect map = mapRect();
        if (chapter == null) {
            fr.drawStringWithShadow("This quest book has no chapters.", map.x() + 140, map.y() + 10, 0xAAAAAA);
        } else {
            // in edit mode the tall side panel hides the right edge of the map; otherwise the map is full screen
            Rect visibleMap = propsOpen() ? new Rect(map.x(), map.y(), map.width() - DETAIL_W, map.height()) : map;
            applyDragFromMouse();
            followMovingImage(chapter);
            scissor(visibleMap);
            drawMap(fr, book, engine, p, chapter, map);
            unscissor();
        }
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        drawChapterList(fr, book, p, chapters, mouseX, mouseY);
        drawHeader(fr, book, engine, p, mouseX, mouseY);
        if (chapter != null && detailOpen()) {
            // item icons write depth values; clear them so later 2D drawing is never hidden behind an icon
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0x78000000);
            scissor(detailRect());
            drawQuestWindow(fr, book, engine, p);
            unscissor();
            if (propsOpen()) {
                GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
                scissor(propsRect());
                drawEditDetail(fr, book);
                unscissor();
            }
        }
        if (searching) {
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            drawSearchResults(fr, book, p, mouseX, mouseY);
        }
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        drawBottomBar(fr, mouseX, mouseY);
        drawMenu(fr, mouseX, mouseY);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        List<String> tip = computeTooltip(mouseX, mouseY);
        boolean overMenu = !menu.isEmpty() && menuRect(menu).contains(mouseX, mouseY);
        if (tip != null && !tip.isEmpty() && !overMenu) { // nothing explains what is behind an open menu
            ScaledResolution res = new ScaledResolution(mc());
            Ui.drawTooltip(fr, tip, mouseX, mouseY, res.getScaledWidth(), res.getScaledHeight());
        }
    }

    /**
     * Pans while the button is held, from the mouse position read once per rendered frame, in real pixels.
     * Mouse <em>events</em> are only handled once per game tick (20 per second), which made dragging choppy.
     */
    private void applyDragFromMouse() {
        if (movingQuest != null) {
            updateGhost(); // the new place follows the mouse until a click drops the quest there
            return;
        }
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

    private static void toggleSearch() {
        searching = !searching;
        if (searching) {
            QUERY.replaceAllText("");
            cachedQuery = null;
        }
    }

    private void refreshResults(QuestBook book, TeamProgress p) {
        String q = QUERY.text();
        if (!q.equals(cachedQuery) || cachedBookVersion != ClientQuests.bookVersion() || cachedProgress != p) {
            cachedQuery = q;
            cachedBookVersion = ClientQuests.bookVersion();
            cachedProgress = p;
            results = QuestSearch.search(book, p, q, 10);
        }
    }

    /** Jump to a search result: open its chapter, centre the map on it and select it. */
    private void pickResult(QuestSearch.Result r) {
        QuestBook book = ClientQuests.book();
        Chapter c = book == null ? null : book.chapter(r.chapterId());
        Quest q = book == null ? null : book.quest(r.questId());
        if (c == null || q == null) {
            return;
        }
        chapterId = c.id;
        selectedQuest = q.id;
        Rect map = mapRect();
        viewFor(c, map).centerOn(q.x * GRID, q.y * GRID, map.x() + map.width() / 2.0, map.y() + map.height() / 2.0);
        searching = false;
    }

    private record SearchRow(boolean chapter, String id, String chapterId, String title, String shown, DataNode.Compound icon) {}

    private static ItemStack searchFallbackIcon(SearchRow row) {
        QuestBook b = ClientQuests.book();
        if (b == null) {
            return null;
        }
        if (row.chapter()) {
            Chapter c = b.chapter(row.id());
            return c == null ? null : chapterIconFromQuests(c);
        }
        Quest q = b.quest(row.id());
        return q == null ? null : QuestText.iconOf(q);
    }

    private static List<SearchRow> searchRows = List.of();
    private static String rowsQuery = null;
    private static int rowsVersion = -1;
    private static TeamProgress rowsProgress;
    private static int searchScroll;
    private static final int SEARCH_ROW = 14;

    private void buildRows(QuestBook book, TeamProgress p) {
        String q = QUERY.text();
        if (q.equals(rowsQuery) && rowsVersion == ClientQuests.bookVersion() && rowsProgress == p) {
            return;
        }
        rowsQuery = q;
        rowsVersion = ClientQuests.bookVersion();
        rowsProgress = p;
        searchScroll = 0;
        List<String> words = new ArrayList<>();
        for (String w : q.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        List<SearchRow> chapters = new ArrayList<>();
        List<SearchRow> quests = new ArrayList<>();
        for (Chapter c : book.chapters()) {
            if (c.alwaysInvisible && !isEditing()) {
                continue;
            }
            String plain = RichText.stripCodes(c.title.isEmpty() ? c.id : c.title);
            if (matches(words, plain)) {
                chapters.add(new SearchRow(true, c.id, c.id, plain, RichText.toRainbowAware(c.title.isEmpty() ? c.id : c.title), c.icon));
            }
            for (Quest quest : c.quests) {
                if (!QuestRules.isVisible(book, p, quest) || !QuestRules.detailsVisible(book, p, quest)) {
                    continue;
                }
                String title = RichText.stripCodes(QuestText.rawTitle(quest));
                StringBuilder hay = new StringBuilder(title).append('\n').append(RichText.stripCodes(quest.subtitle));
                for (String line : quest.description) {
                    hay.append('\n').append(RichText.stripCodes(line));
                }
                if (!words.isEmpty() && matches(words, hay.toString() + "\n" + quest.id)) {
                    quests.add(new SearchRow(false, quest.id, c.id, title, RichText.toRainbowAware(QuestText.rawTitle(quest)), quest.icon));
                }
            }
        }
        java.util.Comparator<SearchRow> byTitle = java.util.Comparator.comparing(r -> r.title().toLowerCase(java.util.Locale.ROOT));
        chapters.sort(byTitle);
        quests.sort(byTitle);
        List<SearchRow> all = new ArrayList<>(chapters);
        all.addAll(quests.size() > 400 ? quests.subList(0, 400) : quests);
        searchRows = all;
    }

    private static boolean matches(List<String> words, String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String w : words) {
            if (!lower.contains(w)) {
                return false;
            }
        }
        return true;
    }

    /** Jump to a search row: a chapter opens that chapter; a quest opens it and centres the map on it. */
    private void pickRow(SearchRow row) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return;
        }
        if (row.chapter()) {
            chapterId = row.id();
            selectedQuest = "";
            searching = false;
        } else {
            pickResult(new QuestSearch.Result(row.id(), row.chapterId(), row.title(), ""));
        }
    }

    private Rect searchWindow() {
        int w = Math.min(330, bounds.width() - 40);
        int h = Math.min(260, bounds.height() - 40);
        return new Rect(bounds.x() + (bounds.width() - w) / 2, bounds.y() + (bounds.height() - h) / 2, w, h);
    }

    /** "Select Quest Object": a window with a text field and an alphabetical list of chapters, then quests. */
    private void drawSearchResults(FontRenderer fr, QuestBook book, TeamProgress p, int mouseX, int mouseY) {
        buildRows(book, p);
        Rect win = searchWindow();
        Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0x99000000);
        Ui.box(win, 0xF0252932);
        fr.drawStringWithShadow("Select Quest Object", win.x() + 7, win.y() + 7, 0xFFFFFF);
        Rect close = new Rect(win.x() + win.width() - 16, win.y() + 6, 10, 10);
        if (!Ui.questsGui("close", close.x(), close.y(), 10, 10, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("x", close.x() + 2, close.y() + 1, 0xFF5555);
        }
        hits.add(new Hit(close, () -> searching = false, "Close"));
        Rect field = new Rect(win.x() + 6, win.y() + 20, win.width() - 26, 14);
        Gui.drawRect(field.x(), field.y(), field.x() + field.width(), field.y() + field.height(), 0xFF4C7BC4);
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        RewardTablesScreen.drawFieldText(fr, field, QUERY);
        Rect list = new Rect(win.x() + 6, win.y() + 38, win.width() - 22, win.height() - 44);
        Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF181A20);
        int visible = list.height() / SEARCH_ROW;
        int maxScroll = Math.max(0, searchRows.size() - visible);
        searchScroll = Math.max(0, Math.min(maxScroll, searchScroll));
        for (int i = 0; i < visible && searchScroll + i < searchRows.size(); i++) {
            SearchRow row = searchRows.get(searchScroll + i);
            Rect rr = new Rect(list.x() + 1, list.y() + i * SEARCH_ROW, list.width() - 2, SEARCH_ROW);
            if (rr.contains(mouseX, mouseY)) {
                Gui.drawRect(rr.x(), rr.y(), rr.x() + rr.width(), rr.y() + rr.height(), 0x55FFFFFF);
            }
            ItemStack icon = row.icon().entries().isEmpty() ? searchFallbackIcon(row) : QuestText.stackOf(row.icon());
            int tx = rr.x() + 3;
            if (icon != null && !icon.isEmpty()) {
                drawItem(icon, tx, rr.y() + 3, 0.5f);
                tx += 12;
            }
            Ui.drawText(fr, row.shown(), tx, rr.y() + 3, 0xFFE060, rr.x() + rr.width() - tx - 3);
            hits.add(new Hit(rr, () -> pickRow(row), null));
        }
        if (searchRows.isEmpty() && QUERY.text().length() > 0) {
            fr.drawStringWithShadow("Nothing found", list.x() + 5, list.y() + 5, 0x888888);
        }
        // scroll bar
        Rect bar = new Rect(list.x() + list.width() + 3, list.y(), 8, list.height());
        Gui.drawRect(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), 0xFF181A20);
        if (maxScroll > 0) {
            int knob = Math.max(10, bar.height() * visible / searchRows.size());
            int ky = bar.y() + (bar.height() - knob) * searchScroll / maxScroll;
            Gui.drawRect(bar.x() + 1, ky, bar.x() + bar.width() - 1, ky + knob, 0xFF4C7BC4);
        }
    }

    /** True if the player has a reward that can be claimed right now (choice rewards included). */
    private static boolean anyClaimable(QuestBook book, ProgressEngine engine, TeamProgress p, UUID me) {
        for (Quest q : book.allQuests()) {
            if (claimable(engine, p, me, q)) {
                return true;
            }
        }
        return false;
    }

    private static boolean claimable(ProgressEngine engine, TeamProgress p, UUID me, Quest q) {
        if (me == null || !p.isCompleted(q.id)) {
            return false;
        }
        QuestBook book = ClientQuests.book();
        for (Reward r : q.rewards) {
            if (engine.canClaim(p, me, r) && (book == null || QuestText.grantable(book, r, 0))) {
                return true;
            }
        }
        return false;
    }

    /** A small square button showing a texture from the supplied jar, or a text label when the art is missing. */
    private int iconButton(int x, int y, String texture, String fallback, String tip, Runnable action, int mouseX, int mouseY) {
        FontRenderer fr = mc().fontRenderer;
        boolean art = QuestArt.available() && QuestArt.has("textures/gui/" + texture + ".png");
        Rect r = new Rect(x, y, 20, 20);
        Ui.vanillaButton(r, r.contains(mouseX, mouseY));
        if (art) {
            QuestArt.quad(QuestArt.guiTexture(texture), r.x() + 2, r.y() + 2, 16, 16, 0xFFFFFFFF);
        } else {
            fr.drawStringWithShadow(fallback, r.x() + 10 - fr.getStringWidth(fallback) / 2, r.y() + 6, 0xFFFFFF);
        }
        hits.add(new Hit(r, action, tip));
        return y + 22;
    }

    /** Upstream-style flat widget: translucent fill inside a one-pixel dark border. */
    private static void panelBox(Rect r, int fill) {
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF1B1D1E);
        Gui.drawRect(r.x() + 1, r.y() + 1, r.x() + r.width() - 1, r.y() + r.height() - 1, 0xFF2B2D31);
        Gui.drawRect(r.x() + 1, r.y() + 1, r.x() + r.width() - 1, r.y() + r.height() - 1, fill);
    }

    private void drawHeader(FontRenderer fr, QuestBook book, ProgressEngine engine, TeamProgress p, int mouseX, int mouseY) {
        int left = bounds.x() + listWidth();
        if (searching) {
            // the search window draws its own text field
        } else if (isEditing() && !linkFrom.isEmpty()) {
            String text = "Click the quest that has to be completed first (Esc cancels)";
            fr.drawStringWithShadow(fr.trimStringToWidth(text, bounds.width() - LIST_W - 80), left + 10, bounds.y() + 8, 0xFFB050);
        }
        // button column on the right edge, like the upstream questbook
        int x = bounds.x() + bounds.width() - 26 - (propsOpen() ? DETAIL_W : 0);
        int y = bounds.y() + 6;
        UUID me = mc().player == null ? null : mc().player.getUniqueID();
        refreshAlerts(book, engine, p, me);
        boolean claimable = anyAlert;
        int bagY = y;
        y = iconButton(x, y, "collect_rewards", "$", claimable ? "Select Rewards" : "No rewards to claim", () -> {
            if (anyAlert) {
                mc().displayGuiScreen(new RewardsScreen(mc().currentScreen));
            }
        }, mouseX, mouseY);
        if (claimable) {
            Ui.alert(x + 12, bagY - 3, 9);
        }
        // auto-pin switch (its behaviour comes later; for now it only remembers the setting)
        ClientPrefs.ensureLoaded();
        Rect autoPin = new Rect(x, y, 20, 20);
        Ui.vanillaButton(autoPin, autoPin.contains(mouseX, mouseY));
        if (!Ui.questsGui("pin", x + 2, y + 2, 16, 16, ClientPrefs.autoPinEnabled ? 0xFFF8BD1C : 0xFFDBDBDB)) {
            fr.drawStringWithShadow("P", x + 7, y + 6, ClientPrefs.autoPinEnabled ? 0xF8BD1C : 0xDBDBDB);
        }
        hits.add(new Hit(autoPin, () -> {
            ClientPrefs.autoPinEnabled = !ClientPrefs.autoPinEnabled;
            ClientPrefs.save();
            autoPinNoticeUntil = System.currentTimeMillis() + 2500;
        }, "Auto-pin: " + (ClientPrefs.autoPinEnabled ? "Enabled" : "Disabled")));
        if (System.currentTimeMillis() < autoPinNoticeUntil) {
            String note = "Auto-pin: " + (ClientPrefs.autoPinEnabled ? "Enabled" : "Disabled");
            int nw = fr.getStringWidth(note) + 10;
            Rect box = new Rect(x - nw - 4, y + 2, nw, 16);
            Ui.box(box, 0xF0101014);
            fr.drawStringWithShadow(note, box.x() + 5, box.y() + 4, 0xFFFFFF);
        }
        y += 22;
        // key reference
        Rect keys = new Rect(x, y, 20, 20);
        Ui.vanillaButton(keys, keys.contains(mouseX, mouseY));
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/keyboard.png", x + 2, y + 2, 16, 16, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("K", x + 7, y + 6, 0xFFFFFF);
        }
        hits.add(new Hit(keys, () -> mc().displayGuiScreen(new KeyReferenceScreen(mc().currentScreen)), "Key Reference"));
        // the wiki button below it, as in FTB Quests 26.1.2: asks before it opens the documentation page
        Rect wiki = new Rect(x, y + 22, 20, 20);
        Ui.vanillaButton(wiki, wiki.contains(mouseX, mouseY));
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/info.png", x + 2, y + 24, 16, 16, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("i", x + 8, y + 28, 0x55AAFF);
        }
        final QuestBook wikiBook = book;
        hits.add(new Hit(wiki, () -> onLink(wikiBook, new RichText.Click("open_url", "https://go.ftb.team/docs-quests")), "Open Wiki"));
        // bottom of the column: the editing pen sits above the player preferences button
        int by = bounds.y() + bounds.height() - 26;
        Rect prefs = new Rect(x, by, 20, 20);
        Ui.vanillaButton(prefs, prefs.contains(mouseX, mouseY));
        if (mc().player != null) {
            mc().getTextureManager().bindTexture(mc().player.getLocationSkin());
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawScaledCustomSizeModalRect(prefs.x() + 2, prefs.y() + 2, 8, 8, 8, 8, 16, 16, 64, 64);
            Gui.drawScaledCustomSizeModalRect(prefs.x() + 2, prefs.y() + 2, 40, 8, 8, 8, 16, 16, 64, 64);
        }
        hits.add(new Hit(prefs, () -> mc().displayGuiScreen(new PreferencesScreen(mc().currentScreen)), "Player Preferences\n\u00a77[Ctrl + P]"));
        if (ClientQuests.canEdit()) {
            iconButton(x, by - 22, editing ? "editor" : "editor_white", "E", "Editing Mode for '" + (mc().player == null ? "" : mc().player.getName()) + "' " + (editing ? "Enabled" : "Disabled"), () -> {
                editing = !editing;
                linkFrom = "";
                confirmDelete = false;
                confirmDeleteChapter = false;
            }, mouseX, mouseY);
            if (editing) {
                boolean canUndo = ClientQuests.undoCount() > 0 || TableEdits.hasPending();
                boolean canRedo = ClientQuests.redoCount() > 0 && !TableEdits.hasPending();
                Rect redo = new Rect(x, by - 88, 20, 20);
                Rect undo = new Rect(x, by - 66, 20, 20);
                Ui.vanillaButton(redo, canRedo && redo.contains(mouseX, mouseY));
                Ui.vanillaButton(undo, canUndo && undo.contains(mouseX, mouseY));
                if (!Ui.icon(Ui.LIBRARY, "textures/icons/redo.png", redo.x() + 2, redo.y() + 2, 16, 16, canRedo ? 0xFFFFFFFF : 0x66FFFFFF)) {
                    fr.drawStringWithShadow(">", redo.x() + 7, redo.y() + 6, canRedo ? 0xFFFFFF : 0x666666);
                }
                if (!Ui.icon(Ui.LIBRARY, "textures/icons/undo.png", undo.x() + 2, undo.y() + 2, 16, 16, canUndo ? 0xFFFFFFFF : 0x66FFFFFF)) {
                    fr.drawStringWithShadow("<", undo.x() + 7, undo.y() + 6, canUndo ? 0xFFFFFF : 0x666666);
                }
                hits.add(new Hit(redo, () -> redoEdit(),
                        canRedo ? String.join("\n", ChangeLog.historyTip(false, "Ctrl + Y", ClientQuests.redoLabel())) : "Nothing to redo"));
                hits.add(new Hit(undo, () -> undoEdit(),
                        canUndo ? String.join("\n", ChangeLog.historyTip(true, "Ctrl + Z", TableEdits.hasPending() ? "" : ClientQuests.undoLabel())) : "Nothing to undo"));
                Rect cog = new Rect(x, by - 44, 20, 20);
                Ui.vanillaButton(cog, cog.contains(mouseX, mouseY));
                if (!Ui.questsGui("settings", cog.x() + 2, cog.y() + 2, 16, 16, 0xFFDBDBDB)) {
                    fr.drawStringWithShadow("S", cog.x() + 7, cog.y() + 6, 0xFFFFFF);
                }
                hits.add(new Hit(cog, () -> openMenu(lastMouseX, lastMouseY,
                        new MenuItem("Edit File", "", "settings",  false,
                                () -> mc().displayGuiScreen(new BookSettingsScreen(mc().currentScreen, ClientQuests.book()))),
                        new MenuItem("Reset Progress", "", "lib:undo", false, () -> confirmBook("Reset progress for your team?", "reset_progress")),
                        new MenuItem("Complete Instantly", "", "lib:check", false, () -> confirmBook("Complete this task instantly?", "complete_quest")),
                        new MenuItem("Edit Reward Tables", "", "collect_rewards", false,
                                () -> mc().displayGuiScreen(new RewardTablesScreen(mc().currentScreen))),
                        new MenuItem("Save on Server now", "", "", false, () -> send("save_now", DataNode.Compound.empty()))), "Settings"));
            }
        }
    }

    /** The vanilla Yes/No screen FTB Quests asks before changing the whole book's progress. */
    private void confirmBook(String question, String op) {
        final GuiScreen back = mc().currentScreen;
        mc().displayGuiScreen(new net.minecraft.client.gui.GuiYesNo((result, id) -> {
            mc().displayGuiScreen(back);
            if (result) {
                send(op, args("all", true));
            }
        }, question, "", 0));
    }

    private void drawChapterList(FontRenderer fr, QuestBook book, TeamProgress p, List<Chapter> chapters, int mouseX, int mouseY) {
        // the panel slides out while the mouse is at the left edge (or over it) and folds away again, linearly
        ClientPrefs.ensureLoaded();
        listPinned = ClientPrefs.chapterPanelPinned;
        long now = System.nanoTime();
        double dtMs = listLastNanos == 0 ? 0 : Math.min(100, (now - listLastNanos) / 1e6);
        listLastNanos = now;
        boolean focusTaken = detailOpen() || searching;
        boolean want = listPinned || !focusTaken && (mouseX < bounds.x() + 8 || listAnim > 0 && mouseX < bounds.x() + listWidth() + 6);
        listAnim = Math.max(0, Math.min(1, listAnim + (want ? 1 : -1) * dtMs / 210.0));
        if (listAnim < 1) {
            // fixed handle under the panel; its opacity is the panel's closed-ness (opaque when closed, gone when open)
            // the game's own page arrow from the recipe book (the lit one while the mouse is at the edge)
            Ui.vanillaPageArrow(bounds.x() + 4, bounds.y() + bounds.height() / 2 - 8, mouseX < bounds.x() + 8, (float) (1 - listAnim));
        }
        if (listWidth() <= 0) {
            return;
        }
        updateListFull(fr, book);
        Rect r = new Rect(bounds.x() - (listFull - listWidth()), bounds.y(), listFull, bounds.height());
        QuestArt.tile(QuestArt.DIRT_BLOCK, r.x(), r.y(), r.width(), r.height(), 32, 0xFF7A7A7A); // dirt behind the chapters
        QuestArt.tile(QuestArt.GRASS, r.x(), r.y(), r.width(), 18, 32, 0xFF5A8A3C); // grass behind the title
        Gui.drawRect(r.x() + r.width() - 1, r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF000000);
        Gui.drawRect(r.x() + r.width() - 2, r.y(), r.x() + r.width() - 1, r.y() + r.height(), 0xFF555555);
        // title row: book icon, title, + (editing), pin; set apart from the list by a frame
        hotbarRim(new Rect(r.x() + 2, r.y() + 2, r.width() - 6, 15), 0.55f); // layout measured on FTB Quests 26.1.2: an 18 unit title row
        ItemStack bookIcon = QuestText.stackOf(book.settings.get("icon"));
        int titleX = r.x() + 16;
        if (bookIcon != null) {
            drawItem(bookIcon, r.x() + 2, r.y() + 4, 0.67f);
        }
        Ui.drawText(fr, RichText.toRainbowAware(book.settings.getString("title", "Quest Book")), titleX, r.y() + 5, 0xFFFFFF, r.width() - (titleX - r.x()) - 26);
        Rect pinBox = new Rect(r.x() + r.width() - 15, r.y() + 4, 10, 10);
        if (isEditing()) {
            Rect plusBox = new Rect(pinBox.x() - 14, r.y() + 4, 10, 10);
            if (!Ui.icon(Ui.LIBRARY, "textures/icons/add.png", plusBox.x() - 1, plusBox.y() - 1, 12, 12, 0xFFFFFFFF)) {
                fr.drawStringWithShadow("+", plusBox.x() + 2, plusBox.y() + 1, 0x7BD21E);
            }
            final int menuAtX = plusBox.x();
            final int menuAtY = plusBox.y() + 12;
            hits.add(new Hit(plusBox, () -> addMenu(menuAtX, menuAtY), "Add a chapter or a chapter group"));
        }
        if (!Ui.questsGui("pin", pinBox.x(), pinBox.y(), 10, 10, listPinned ? 0xFFF8BD1C : 0xFFDBDBDB)) {
            fr.drawStringWithShadow("P", pinBox.x() + 2, pinBox.y() + 1, listPinned ? 0xF8BD1C : 0xDBDBDB);
        }
        hits.add(new Hit(pinBox, () -> {
            listPinned = !listPinned;
            ClientPrefs.chapterPanelPinned = listPinned;
            ClientPrefs.save();
        }, listPinned ? "Let the chapter panel hide" : "Keep the chapter panel open"));
        Rect body = new Rect(r.x(), r.y() + 18, r.width() - 1, r.height() - 18);
        scissor(body);
        int y = body.y() - listScroll;
        final int top = y;
        Chapter current = currentChapter(book);
        // auto-open the group of the chapter being looked at, once per chapter change
        if (current != null && !current.groupId.isEmpty() && !current.id.equals(lastListChapter)) {
            EXPANDED_GROUPS.add(current.groupId);
        }
        lastListChapter = current == null ? "" : current.id;
        // a group made from a chapter is drawn as that chapter's own row, with the others nested below it
        java.util.Set<String> parentChapters = new java.util.HashSet<>();
        for (Definitions.ChapterGroup g : book.groups()) {
            String parent = g.raw.getString("chapter", "");
            if (!parent.isEmpty() && book.chapter(parent) != null) {
                parentChapters.add(parent);
            }
        }
        List<Chapter> loose = new ArrayList<>();
        for (Chapter c : chapters) {
            if (parentChapters.contains(c.id)) {
                continue;
            }
            if (c.groupId.isEmpty() || book.groups().stream().noneMatch(g -> g.id.equals(c.groupId))) {
                loose.add(c);
            }
        }
        for (Chapter c : loose) {
            y = chapterRow(fr, book, p, c, body, y, 0, mouseX, mouseY);
        }
        for (Definitions.ChapterGroup g : book.groups()) {
            List<Chapter> inGroup = new ArrayList<>();
            for (Chapter c : chapters) {
                if (c.groupId.equals(g.id)) {
                    inGroup.add(c);
                }
            }
            if (inGroup.isEmpty() && !isEditing()) {
                continue;
            }
            boolean open = EXPANDED_GROUPS.contains(g.id);
            Rect row = new Rect(body.x() + 1, y, body.width() - 2, 17);
            Chapter parentChapter = g.raw.getString("chapter", "").isEmpty() ? null : book.chapter(g.raw.getString("chapter", ""));
            boolean groupSelected = parentChapter != null && parentChapter.id.equals(chapterId);
            if (groupSelected) {
                Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF88898B);
                Gui.drawRect(row.x() + 1, row.y() + 1, row.x() + row.width() - 1, row.y() + row.height() - 1, 0xFF4C4F55);
            } else if (row.contains(mouseX, mouseY)) {
                Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF4C5055);
            }
            // drawn after the selection, so the arrow stays while the chapter that holds the group is open
            if (!Ui.questsGui(open ? "arrow_expanded" : "arrow_collapsed", row.x() + 3, row.y() + 4, 8, 8, 0xFFDDDDDD)) {
                fr.drawString(open ? "v" : ">", row.x() + 3, row.y() + 4, 0xBBBBBB);
            }
            String groupTitle = parentChapter != null ? parentChapter.title : g.title;
            Ui.drawText(fr, groupTitle.isEmpty() ? g.id : RichText.toRainbowAware(groupTitle), row.x() + 13, row.y() + 5,
                    parentChapter != null ? 0xFFFFFF : 0xD0D0D0, row.width() - 15);
            final String gid = g.id;
            final String parentId = parentChapter == null ? "" : parentChapter.id;
            final Definitions.ChapterGroup group = g;
            // FTB Quests' group menu, in its order: Move, Move, Edit, Quick Edit Properties..., Delete, Complete Instantly, Copy ID
            Runnable groupMenu = !isEditing() ? null : () -> openMenu(lastMouseX, lastMouseY,
                    new MenuItem("\"" + RichText.stripCodes(group.title) + "\"", "", "", false, null),
                    new MenuItem("Move", "", "lib:up", true, () -> send("move_group", args("id", gid, "by", -1))),
                    new MenuItem("Move", "", "lib:down", false, () -> send("move_group", args("id", gid, "by", 1))),
                    new MenuItem("Edit", "", "settings", false, () -> {
                        Chapter parent = group.raw.getString("chapter", "").isEmpty() ? null : ClientQuests.book().chapter(group.raw.getString("chapter", ""));
                        mc().displayGuiScreen(parent != null ? TextEditScreen.forChapter(parent) : TextEditScreen.forGroup(group));
                    }),
                    new MenuItem("Quick Edit Properties...", "", "settings", false, () -> openMenu(lastMouseX, lastMouseY,
                            new MenuItem("\"" + RichText.stripCodes(group.title) + "\"", "", "", false, null),
                            new MenuItem("Hold in Chapter...", "", "", true, () -> chooseHoldingChapter(gid)))),
                    new MenuItem("Delete", "", "lib:remove", false, () -> send("delete_group", args("id", gid))),
                    new MenuItem("Complete Instantly", "", "lib:check", false, () -> {
                        QuestBook gb = ClientQuests.book();
                        if (gb != null) {
                            for (Chapter gc : gb.chapters()) {
                                if (gc.groupId.equals(gid)) {
                                    send("complete_quest", args("chapter", gc.id));
                                }
                            }
                        }
                    }),
                    new MenuItem("Copy ID", "", "lib:info", false, () -> GuiScreen.setClipboardString(gid)));
            hits.add(new Hit(row, () -> {
                if (!parentId.isEmpty()) {
                    chapterId = parentId; // the row is a chapter too: open it
                    selectedQuest = "";
                }
                if (!EXPANDED_GROUPS.remove(gid)) {
                    EXPANDED_GROUPS.add(gid);
                }
            }, null, groupMenu));
            if (isEditing()) {
                Rect plus = new Rect(row.x() + row.width() - 12 - (listBar != null ? 7 : 0), row.y() + 3, 10, 10);
                if (!Ui.icon(Ui.LIBRARY, "textures/icons/add.png", plus.x() - 1, plus.y() - 1, 12, 12, 0xFFFFFFFF)) {
                    fr.drawStringWithShadow("+", plus.x() + 2, plus.y() + 1, 0x7BD21E);
                }
                final int promptX = plus.x();
                final int promptY = plus.y() + 12;
                hits.add(new Hit(plus, () -> mc().displayGuiScreen(new NamePrompt(mc().currentScreen, promptX, promptY, "Chapter", 120,
                        name -> send("create_chapter", args("title", name, "group", gid)))), "Add a chapter to this group"));
            }
            y += 17;
            if (open) {
                for (Chapter c : inGroup) {
                    y = chapterRow(fr, book, p, c, body, y, 7, mouseX, mouseY);
                }
            }
        }
        listContentHeight = y - top + 4;
        int viewH = body.height();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listContentHeight - viewH))); // snaps back to what is there
        listBar = null;
        if (listContentHeight > viewH) {
            // the game's own list scroll bar, thin; click or drag it to scroll
            int bx = r.x() + r.width() - 6;
            Gui.drawRect(bx, body.y(), bx + 4, body.y() + viewH, 0xFF000000);
            int th = Math.max(14, viewH * viewH / listContentHeight);
            int ty = body.y() + (viewH - th) * listScroll / Math.max(1, listContentHeight - viewH);
            Gui.drawRect(bx, ty, bx + 4, ty + th, 0xFF808080);
            Gui.drawRect(bx, ty, bx + 3, ty + th - 1, 0xFFC0C0C0);
            listBar = new Rect(bx - 2, body.y(), 8, viewH);
            listBarThumb = th;
        }
        unscissor();
    }


    /** The game's button click, for what the player presses in the quest book. */
    private static void playClick() {
        mc().getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static final net.minecraft.util.ResourceLocation WIDGETS = new net.minecraft.util.ResourceLocation("textures/gui/widgets.png");

    /** The bottom edge of the hotbar's selected-slot border, stretched under a row; alpha makes it see-through. */
    private static void hotbarRim(Rect r, float alpha) {
        mc().getTextureManager().bindTexture(WIDGETS);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, alpha);
        int c = 3;
        Gui.drawScaledCustomSizeModalRect(r.x() + c, r.y() + r.height() - c, c, 22 + 24 - c, 24 - 2 * c, c, r.width() - 2 * c, c, 256, 256);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** The + in the title row: a chapter or a chapter group, each asking for its name in a small box. */
    private void addMenu(int x, int y) {
        GuiScreen screen = mc().currentScreen;
        openMenu(x, y,
                new MenuItem("Chapter", "", "lib:add", false, () -> mc().displayGuiScreen(new NamePrompt(screen, x, y, "Chapter", 120,
                        name -> send("create_chapter", args("title", name))))),
                new MenuItem("Chapter Group", "", "lib:add", false, () -> mc().displayGuiScreen(new NamePrompt(screen, x, y, "Chapter Group", 120,
                        name -> send("create_group", args("title", name, "id", RewardTablesScreen.newId()))))));
    }

    /** A menu of the chapters that can hold a group, and "no chapter" to let it go. */
    private void chooseHoldingChapter(String groupId) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("(no chapter)", "", "", false, () -> send("set_group_chapter", args("id", groupId, "chapter", ""))));
        for (Chapter c : book.chapters()) {
            if (c.groupId.equals(groupId)) {
                continue; // a chapter cannot hold the group it is in
            }
            final String cid = c.id;
            items.add(new MenuItem(RichText.stripCodes(c.title.isEmpty() ? c.id : c.title), "", "", false,
                    () -> send("set_group_chapter", args("id", groupId, "chapter", cid))));
        }
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    /** A menu with every group (and "no group") to put a chapter into. */
    private void chooseGroup(String chapterId) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Move to which group?", "", "", false, null));
        items.add(new MenuItem("(no group)", "", "", true, () -> send("set_chapter_group", args("id", chapterId, "group", ""))));
        for (Definitions.ChapterGroup g : book.groups()) {
            final String gid = g.id;
            items.add(new MenuItem(RichText.stripCodes(g.title.isEmpty() ? g.id : g.title), "", "", false,
                    () -> send("set_chapter_group", args("id", chapterId, "group", gid))));
        }
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    /** Icons per row in a quest window's Tasks or Rewards column: at most 4, spread evenly over the rows needed. */
    private static int balancedPerRow(int slots) {
        int rows = Math.max(1, (slots + 3) / 4);
        return Math.max(1, (slots + rows - 1) / rows);
    }

    private int chapterRow(FontRenderer fr, QuestBook book, TeamProgress p, Chapter c, Rect body, int y, int indent, int mouseX, int mouseY) {
        Rect row = new Rect(body.x() + 1, y, body.width() - 2, 14);
        boolean selected = c.id.equals(chapterId);
        boolean hover = row.contains(mouseX, mouseY);
        if (hover && !selected) {
            Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF4C5055);
        }
        if (selected) {
            // FTB Quests 26.1.2: a grey frame around a lighter fill
            Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF88898B);
            Gui.drawRect(row.x() + 1, row.y() + 1, row.x() + row.width() - 1, row.y() + row.height() - 1, 0xFF4C4F55);
        }
        // FTB Quests 26.1.2: the picture takes a slot of its own, the name starts 15 units from the panel edge (22 inside a group)
        int tx = row.x() + 15 + indent;
        boolean chapterDone = p != null && QuestRules.chapterCompleted(p, c);
        // the chapter's own icon; without one, the icon of its only quest, or the quests' icons one after the other
        ItemStack icon = c.icon.entries().isEmpty() ? chapterIconFromQuests(c) : QuestText.stackOf(c.icon);
        if (chapterDone) {
            if (!Ui.questsGui("check_gray", row.x() + 1 + indent, row.y() + 2, 10, 10, 0xFFFFFFFF)) {
                fr.drawString("v", row.x() + 3 + indent, row.y() + 3, 0x888888);
            }
        } else if (icon != null && !icon.isEmpty()) {
            drawItem(icon, row.x() + 1 + indent, row.y() + 2, 0.67f);
        }
        UUID me = mc().player == null ? null : mc().player.getUniqueID();
        ProgressEngine engine = ClientQuests.engine();
        if (engine != null) {
            refreshAlerts(book, engine, p, me);
        }
        boolean alert = CHAPTER_ALERT.getOrDefault(c.id, false);
        int barRoom = listBar != null ? 7 : 0;
        int textRight = row.x() + row.width() - (alert ? 13 : 3) - barRoom;
        int nameColour = 0xFFFFFF;
        if (p != null) {
            if (QuestRules.chapterCompleted(p, c)) {
                nameColour = 0x00AA00; // completed: dark green
            } else if (c.quests.stream().anyMatch(cq -> p.isStarted(cq.id) || p.isCompleted(cq.id))) {
                nameColour = 0x00AAAA; // started: teal
            }
        }
        Ui.drawText(fr, c.title.isEmpty() ? c.id : RichText.toRainbowAware(c.title), tx, row.y() + 3, hover ? 0xFFFFA0 : nameColour, textRight - tx);
        if (alert) {
            Ui.alert(row.x() + row.width() - 11 - barRoom, row.y() + 3, 8);
        }
        final String id = c.id;
        Runnable rightClick = !isEditing() ? null : () -> chapterMenu(c);
        hits.add(new Hit(row, () -> {
            chapterId = id;
            selectedQuest = "";
        }, null, rightClick));
        if (isEditing() && c.quests.isEmpty()) {
            Rect cross = new Rect(row.x() + row.width() - 14 - barRoom - (alert ? 10 : 0), row.y() + 2, 10, 10);
            if (!Ui.questsGui("close", cross.x(), cross.y(), 10, 10, 0xFFFFFFFF)) {
                fr.drawStringWithShadow("x", cross.x() + 2, cross.y() + 1, 0xFF5555);
            }
            // the cross only shows that the chapter has no quests; clicking it does nothing
        }
        return y + 14;
    }

    private void drawMap(FontRenderer fr, QuestBook book, ProgressEngine engine, TeamProgress p, Chapter chapter, Rect map) {
        ViewTransform v = viewFor(chapter, map);
        double zoom = v.zoom();
        boolean mouseOnMap = !detailOpen() && !searching && map.contains(lastMouseX, lastMouseY) && !listRect().contains(lastMouseX, lastMouseY);
        Definitions.ChapterImage overImage = mouseOnMap && hoverQuest.isEmpty() ? imageAt(chapter, v, lastMouseX, lastMouseY) : null;
        String nowOver = overImage == null ? "" : overImage.id();
        if (!nowOver.equals(hoverImage)) {
            hoverImageSince = System.currentTimeMillis();
        }
        hoverImage = nowOver;
        refreshLinkNodes(book, chapter);
        drawChapterImages(v, chapter, map);
        if (movingQuest != null) {
            drawMoveGrid(v, map);
        }
        // dependency lines first, then nodes on top
        for (Quest q : chapter.quests) {
            double sx = v.screenX(q.x * GRID);
            double sy = v.screenY(q.y * GRID);
            if (q.dependencies.isEmpty() || !shown(book, p, q)) {
                continue;
            }
            for (String depId : q.dependencies) {
                Quest dep = book.quest(depId);
                if (dep == null || book.chapterOf(depId) != chapter || !shown(book, p, dep)) {
                    continue;
                }
                String focus = !hoverQuest.isEmpty() ? hoverQuest : selectedQuest;
                boolean touchesFocus = q.id.equals(focus) || depId.equals(focus);
                if ((q.hideDependencyLines.resolve(chapter.defaultHideDependencyLines) || dep.hideDependentLines.resolve(false)) && !touchesFocus) {
                    continue; // a hidden line only shows while one of its two quests is hovered or open
                }
                double dx = v.screenX(dep.x * GRID);
                double dy = v.screenY(dep.y * GRID);
                if (Math.max(sx, dx) < map.x() || Math.min(sx, dx) > map.x() + map.width()
                        || Math.max(sy, dy) < map.y() || Math.min(sy, dy) > map.y() + map.height()) {
                    continue;
                }
                // straight line from node to node; green while the hovered or opened quest is one of its ends
                int tint = 0xFF7A6B71; // calm by default (measured from FTB Quests)
                int flow = 0;          // lines are still, unless a quest is hovered
                if (q.id.equals(focus)) {
                    tint = 0xFF00C4C4; // a dependency of the focused quest: cyan, moving forward towards the focused quest
                    flow = 1;
                } else if (depId.equals(focus)) {
                    tint = 0xFFC4C400; // a dependant of the focused quest: yellow, moving on to the dependant
                    flow = 1;
                }
                drawDependency(q, depId, dx, dy, sx, sy, v, Math.max(1.0, Math.min(6.0, zoom * 2.45)), tint, flow); // 2.45 GUI pixels wide at zoom 1: the line is 4.1% of the distance between neighbouring quests, as in FTB Quests 26.1.2
            }
        }
        if (movingQuest != null && chapter.quests.contains(movingQuest)) {
            drawMoveTarget(book, chapter, v); // under the quests, so the one being moved is never covered by it
        }
        for (Quest q : drawOrder(chapter)) {
            double size = NODE * com.levviata.reflibquests.model.Presets.size(book, q) * zoom;
            double sx = v.screenX(q.x * GRID) - size / 2;
            double sy = v.screenY(q.y * GRID) - size / 2;
            if (sx + size < map.x() || sx > map.x() + map.width() || sy + size < map.y() || sy > map.y() + map.height()
                    || !shown(book, p, q)) {
                continue;
            }
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT); // whatever was drawn before must not poke through this quest
            State state = stateOf(book, engine, p, q);
            int x = (int) Math.round(sx);
            int y = (int) Math.round(sy);
            int s = Math.max(4, (int) Math.round(size));
            boolean selected = q.id.equals(selectedQuest);
            String shape = QuestArt.shapeName(book, chapter, q);
            // upstream look: grey body, light outline; completed = green outline; locked = dark with a lock
            boolean lockedByDeps = state != State.DONE && !QuestRules.dependenciesMet(book, p, q);
            int body = lockedByDeps ? 0xFF4A4C52 : 0xFF9C9EA6;
            int edge = selected ? 0xFFFFFFFF : state == State.DONE ? 0xFF6BD36B : lockedByDeps ? 0xFF5E6066 : 0xFFC9CBD2;
            // hovering lights the node up a little; the opened quest slides back and forth between its plain and lit colours
            boolean beingMoved = movingQuest != null && (movingQuest.id.equals(q.id) || moveGroup.contains(q));
            boolean picked = isEditing() && SELECTION.contains(q.id); // picked in the editor: pulses like an opened quest
            float glow = beingMoved ? questPulse() : q.id.equals(hoverQuest) ? 1f : selected || picked ? questPulse() : 0f;
            if (glow > 0f) {
                float strength = q.id.equals(hoverQuest) && !selected && !picked && !beingMoved ? 0.24f : 0.7f; // a pick pulses plain to bright
                body = lighten(body, strength * glow);
                edge = lighten(edge, strength * glow);
            }

            if (QuestArt.hasShape(shape)) {
                // art from the player's own FTB Quests jar: the shape tinted by state, with its outline on top
                net.minecraft.util.ResourceLocation solid = Ui.opaqueShape(shape);
                QuestArt.quad(solid != null ? solid : QuestArt.shapeTexture(shape, "background"), x, y, s, s, body);
                QuestArt.quad(QuestArt.shapeTexture(shape, "outline"), x, y, s, s, edge);
            } else {
                rect(x - 1, y - 1, s + 2, s + 2, edge);
                rect(x, y, s, s, body);
            }
            if (s >= 6) {
                // the icon is always the same fraction of its square, so it grows and shrinks with the zoom
                float iconSize = (float) (s * ICON_FRACTION * q.iconScale); // FTB 'Icon Scaling', independent of the quest's size
                drawQuestIcon(q, x + (s - iconSize) / 2f, y + (s - iconSize) / 2f, iconSize, state == State.DONE);
            }
            // the small lock sits on top of the icon: a quest whose dependencies are not all completed yet
            if (lockedByDeps && s >= 14 && book.settings.getBool("show_lock_icons", ClientPrefs.showLockIcon) && !q.hideLockIcon) {
                int lock = Math.max(7, s / 3);
                GlStateManager.pushMatrix();
                GlStateManager.translate(0, 0, 400);
                Ui.questsGui("quest_locked", x + s - lock - 1, y + s - lock - 1, lock, lock, 0xFFFFFFFF);
                GlStateManager.popMatrix();
            }
            if (s >= 14) {
                UUID me = mc().player == null ? null : mc().player.getUniqueID();
                double badge = Math.max(7, s * 0.33);
                // top-right: the badge's centre sits about a sixth of the node inside the corner
                if (claimable(engine, p, me, q)) {
                    Ui.alert(x + s * 0.83 - badge / 2, y + s * 0.17 - badge / 2, badge);
                } else if (state == State.DONE) {
                    Ui.check(x + s * 0.83 - badge / 2, y + s * 0.17 - badge / 2, badge, 0xFFFFFFFF);
                }
                // while editing, a quest players cannot see yet carries a small eye in its top left corner
                if (isEditing() && !QuestRules.isVisible(book, p, q)) {
                    Ui.questsGui("hidden", x + s * 0.20 - badge / 2, y + s * 0.19 - badge / 2, badge, badge, 0xFFFFFFFF);
                }
            }
        }
        syncSelection(chapter);
        if (boxing) {
            int bx0 = Math.min(boxX0, lastMouseX);
            int by0 = Math.min(boxY0, lastMouseY);
            int bx1 = Math.max(boxX0, lastMouseX);
            int by1 = Math.max(boxY0, lastMouseY);
            rect(bx0, by0, bx1 - bx0, by1 - by0, 0x33FFFFFF);
            rect(bx0, by0, bx1 - bx0, 1, 0xCCFFFFFF);
            rect(bx0, by1, bx1 - bx0, 1, 0xCCFFFFFF);
            rect(bx0, by0, 1, by1 - by0, 0xCCFFFFFF);
            rect(bx1, by0, 1, by1 - by0 + 1, 0xCCFFFFFF);
        }
        if (!imgMode.isEmpty()) {
            drawImageMode(fr, book, v, chapter);
        }
        if (!resizeQuest.isEmpty()) {
            drawResize(fr, book, v, zoom);
        }
        if (!bezierQuest.isEmpty()) {
            if (!bezierGrabDep.isEmpty()) {
                double[] c = bezierPts.get(bezierGrabDep);
                if (c != null && Mouse.isButtonDown(0)) {
                    c[bezierGrabIndex * 2] = v.worldX(lastMouseX) / GRID;
                    c[bezierGrabIndex * 2 + 1] = v.worldY(lastMouseY) / GRID;
                } else {
                    bezierGrabDep = "";
                }
            }
            drawBezierHandles(fr, book, v);
        }
        if (crosshairs && isEditing() && mouseOnMap) {
            int cxl = (int) Math.round(v.screenX(Math.round(v.worldX(lastMouseX) / GRID * 2.0) / 2.0 * GRID));
            int cyl = (int) Math.round(v.screenY(Math.round(v.worldY(lastMouseY) / GRID * 2.0) / 2.0 * GRID));
            rect(cxl, map.y(), 1, map.height(), 0x88FFFFFF);
            rect(map.x(), cyl, map.width(), 1, 0x88FFFFFF);
        }
        if (isEditing() && movingQuest == null && !boxing && mouseOnMap && hoverQuest.isEmpty() && hoverImage.isEmpty() && menu.isEmpty()) {
            // hovering free space in edit mode: the rim of the quest a click would create, snapped to the grid
            Quest sample = new Quest("_");
            String rimShape = QuestArt.shapeName(book, chapter, sample);
            double rsize = NODE * sample.size * zoom;
            int rs = Math.max(4, (int) Math.round(rsize));
            int rx = (int) Math.round(v.screenX(Math.round(v.worldX(lastMouseX) / GRID * 2.0) / 2.0 * GRID) - rsize / 2);
            int ry = (int) Math.round(v.screenY(Math.round(v.worldY(lastMouseY) / GRID * 2.0) / 2.0 * GRID) - rsize / 2);
            if (QuestArt.hasShape(rimShape)) {
                QuestArt.quad(QuestArt.shapeTexture(rimShape, "outline"), rx, ry, rs, rs, 0x58FFFFFF);
            } else {
                rect(rx, ry, rs, 1, 0x58FFFFFF);
                rect(rx, ry + rs - 1, rs, 1, 0x58FFFFFF);
                rect(rx, ry, 1, rs, 0x58FFFFFF);
                rect(rx + rs - 1, ry, 1, rs, 0x58FFFFFF);
            }
        }
    }

    /** While a quest is being moved: the grid it snaps to (every half unit, whole units a little stronger). */
    private void drawMoveGrid(ViewTransform v, Rect map) {
        double u0 = v.worldX(map.x()) / GRID;
        double u1 = v.worldX(map.x() + map.width()) / GRID;
        double w0 = v.worldY(map.y()) / GRID;
        double w1 = v.worldY(map.y() + map.height()) / GRID;
        if ((u1 - u0) > 150 || (w1 - w0) > 150) {
            return; // zoomed so far out that the lines would only be a haze
        }
        for (int k = (int) Math.ceil(u0 * 2); k <= (int) Math.floor(u1 * 2); k++) {
            rect((int) Math.round(v.screenX(k / 2.0 * GRID)), map.y(), 1, map.height(), k % 2 == 0 ? 0x38FFFFFF : 0x18FFFFFF);
        }
        for (int k = (int) Math.ceil(w0 * 2); k <= (int) Math.floor(w1 * 2); k++) {
            rect(map.x(), (int) Math.round(v.screenY(k / 2.0 * GRID)), map.width(), 1, k % 2 == 0 ? 0x38FFFFFF : 0x18FFFFFF);
        }
    }

    /** The place a quest being moved will land on: its shape, pale, with no border; drawn under the quests. */
    private void drawMoveTarget(QuestBook book, Chapter chapter, ViewTransform v) {
        List<Quest> all = new ArrayList<>();
        all.add(movingQuest);
        all.addAll(moveGroup);
        for (Quest mq : all) {
            double gsize = NODE * com.levviata.reflibquests.model.Presets.size(book, mq) * v.zoom();
            int gs = Math.max(4, (int) Math.round(gsize));
            int gx = (int) Math.round(v.screenX((ghostX + (mq.x - movingQuest.x)) * GRID) - gsize / 2);
            int gy = (int) Math.round(v.screenY((ghostY + (mq.y - movingQuest.y)) * GRID) - gsize / 2);
            String gshape = QuestArt.shapeName(book, chapter, mq);
            if (QuestArt.hasShape(gshape)) {
                net.minecraft.util.ResourceLocation solid = Ui.opaqueShape(gshape);
                QuestArt.quad(solid != null ? solid : QuestArt.shapeTexture(gshape, "background"), gx, gy, gs, gs, 0x55FFFFFF);
            } else {
                rect(gx, gy, gs, gs, 0x55FFFFFF);
            }
        }
    }

    /** 0 to 1 and back to 0 in a straight line, once every two seconds. */

    private static int mixColour(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** The control points of a line in grid units: the working ones while they are being edited, else the saved ones. */
    private double[] controlPoints(Quest q, String depId) {
        if (bezierQuest.equals(q.id) && bezierPts.containsKey(depId)) {
            return bezierPts.get(depId);
        }
        List<Double> saved = q.depControlPts.get(depId);
        return saved == null || saved.size() != 4 ? null : new double[] {saved.get(0), saved.get(1), saved.get(2), saved.get(3)};
    }

    /** One dependency line: straight, or along its Bezier curve from the dependency (dx, dy) to the quest (sx, sy). */
    private void drawDependency(Quest q, String depId, double dx, double dy, double sx, double sy, ViewTransform v, double thick, int tint, int flow) {
        double[] c = controlPoints(q, depId);
        if (c == null) {
            ladderLine(dx, dy, sx, sy, thick, tint, flow);
            return;
        }
        int n = 40;
        double[] xs = new double[n + 1];
        double[] ys = new double[n + 1];
        double c1x = v.screenX(c[0] * GRID);
        double c1y = v.screenY(c[1] * GRID);
        double c2x = v.screenX(c[2] * GRID);
        double c2y = v.screenY(c[3] * GRID);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double u = 1 - t;
            xs[i] = u * u * u * dx + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * sx;
            ys[i] = u * u * u * dy + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * sy;
        }
        ladderPath(xs, ys, n + 1, thick, tint, flow);
    }

    /** As {@link #ladderLine}, along a bent path; the texture runs on without a break from one piece to the next. */
    private static void ladderPath(double[] xs, double[] ys, int count, double thickness, int argb, int flow) {
        if (count < 2) {
            return;
        }
        boolean chevrons = QuestArt.hasGui("dependency");
        double phase = (System.currentTimeMillis() % 20000L) / 20000.0 * 8.0;
        double[] nx = new double[count];
        double[] ny = new double[count];
        double[] vv = new double[count];
        double along = 0;
        for (int i = 0; i < count; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(count - 1, i + 1);
            double tx = xs[b] - xs[a];
            double ty = ys[b] - ys[a];
            double len = Math.sqrt(tx * tx + ty * ty);
            if (len < 1e-6) {
                tx = 1;
                ty = 0;
                len = 1;
            }
            nx[i] = -ty / len * thickness / 2;
            ny[i] = tx / len * thickness / 2;
            if (i > 0) {
                along += Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            }
            vv[i] = along / (chevrons ? thickness : thickness * 8) - flow * phase;
        }
        mc().getTextureManager().bindTexture(chevrons ? QuestArt.guiTexture("dependency") : LADDER);
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, 1f);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        for (int i = 0; i + 1 < count; i++) {
            if (chevrons) {
                bb.pos(xs[i] + nx[i], ys[i] + ny[i], 0).tex(vv[i], 0).endVertex();
                bb.pos(xs[i + 1] + nx[i + 1], ys[i + 1] + ny[i + 1], 0).tex(vv[i + 1], 0).endVertex();
                bb.pos(xs[i + 1] - nx[i + 1], ys[i + 1] - ny[i + 1], 0).tex(vv[i + 1], 1).endVertex();
                bb.pos(xs[i] - nx[i], ys[i] - ny[i], 0).tex(vv[i], 1).endVertex();
            } else {
                bb.pos(xs[i] + nx[i], ys[i] + ny[i], 0).tex(2 / 16.0, vv[i]).endVertex();
                bb.pos(xs[i + 1] + nx[i + 1], ys[i + 1] + ny[i + 1], 0).tex(2 / 16.0, vv[i + 1]).endVertex();
                bb.pos(xs[i + 1] - nx[i + 1], ys[i + 1] - ny[i + 1], 0).tex(4 / 16.0, vv[i + 1]).endVertex();
                bb.pos(xs[i] - nx[i], ys[i] - ny[i], 0).tex(4 / 16.0, vv[i]).endVertex();
            }
        }
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableCull();
    }

    // ------------------------------------------------------------------ Bezier editing

    /** The quest whose dependency lines are being bent, and the working control points of each ({@code x1, y1, x2, y2}, grid units). */
    private String bezierQuest = "";
    private final java.util.Map<String, double[]> bezierPts = new java.util.LinkedHashMap<>();
    /** The handle being dragged: the dependency id and 0 (by the dependency) or 1 (by the quest), or empty. */
    private String bezierGrabDep = "";
    private int bezierGrabIndex;

    private void startBezier(Quest q) {
        QuestBook book = ClientQuests.book();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter == null) {
            return;
        }
        bezierQuest = q.id;
        bezierPts.clear();
        bezierGrabDep = "";
        selectedQuest = "";
        for (String depId : q.dependencies) {
            Quest dep = book.quest(depId);
            if (dep == null || book.chapterOf(depId) != chapter) {
                continue;
            }
            List<Double> saved = q.depControlPts.get(depId);
            bezierPts.put(depId, saved != null && saved.size() == 4
                    ? new double[] {saved.get(0), saved.get(1), saved.get(2), saved.get(3)}
                    : new double[] {dep.x, dep.y, q.x, q.y}); // untouched handles sit on their quests: a straight line
        }
    }

    private void endBezier(boolean accept) {
        QuestBook book = ClientQuests.book();
        Quest q = book == null ? null : book.quest(bezierQuest);
        if (accept && q != null) {
            DataNode.Compound points = DataNode.Compound.empty();
            for (java.util.Map.Entry<String, double[]> e : bezierPts.entrySet()) {
                Quest dep = book.quest(e.getKey());
                double[] c = e.getValue();
                boolean straight = dep != null && c[0] == dep.x && c[1] == dep.y && c[2] == q.x && c[3] == q.y;
                if (straight) {
                    continue; // both handles still on their quests
                }
                List<DataNode> nums = new ArrayList<>();
                for (double d : c) {
                    nums.add(new DataNode.Dbl(Math.round(d * 1000.0) / 1000.0));
                }
                points = points.with(e.getKey(), new DataNode.Lst(nums));
            }
            send("set_bezier", DataNode.Compound.empty().with("id", q.id).with("points", points));
        }
        bezierQuest = "";
        bezierPts.clear();
        bezierGrabDep = "";
    }

    /** The handle under a point: {dependency id, index} or null. */
    private Object[] handleAt(ViewTransform v, int mouseX, int mouseY) {
        for (java.util.Map.Entry<String, double[]> e : bezierPts.entrySet()) {
            double[] c = e.getValue();
            for (int i = 1; i >= 0; i--) {
                double hx = v.screenX(c[i * 2] * GRID);
                double hy = v.screenY(c[i * 2 + 1] * GRID);
                if (Math.abs(mouseX - hx) <= 8 && Math.abs(mouseY - hy) <= 8) {
                    return new Object[] {e.getKey(), i};
                }
            }
        }
        return null;
    }

    /** Handles and their thin lines on top of the quests, and the hint at the top right. */
    private void drawBezierHandles(FontRenderer fr, QuestBook book, ViewTransform v) {
        Quest q = book.quest(bezierQuest);
        if (q == null) {
            return;
        }
        for (java.util.Map.Entry<String, double[]> e : bezierPts.entrySet()) {
            Quest dep = book.quest(e.getKey());
            if (dep == null) {
                continue;
            }
            double[] c = e.getValue();
            double[][] anchors = {{dep.x, dep.y}, {q.x, q.y}};
            for (int i = 0; i < 2; i++) {
                double ax = v.screenX(anchors[i][0] * GRID);
                double ay = v.screenY(anchors[i][1] * GRID);
                double hx = v.screenX(c[i * 2] * GRID);
                double hy = v.screenY(c[i * 2 + 1] * GRID);
                lineBetween(ax, ay, hx, hy, 0xAA7678B8);
            }
        }
        for (java.util.Map.Entry<String, double[]> e : bezierPts.entrySet()) {
            double[] c = e.getValue();
            for (int i = 0; i < 2; i++) {
                double hx = v.screenX(c[i * 2] * GRID);
                double hy = v.screenY(c[i * 2 + 1] * GRID);
                if (!Ui.icon(Ui.LIBRARY, "textures/icons/marker.png", hx - 8, hy - 8, 16, 16, 0xFFFFFFFF)) {
                    rect((int) hx - 3, (int) hy - 3, 7, 7, 0xFF66CCFF);
                }
            }
        }
        String hint = "B\u00e9zier Editing | [Enter] Accept | [Esc] Cancel";
        fr.drawStringWithShadow(hint, bounds.x() + bounds.width() - fr.getStringWidth(hint) - 34, bounds.y() + 6, 0xFFFFFF);
    }

    /** A thin line, one pixel wide, between two screen points. */
    private static void lineBetween(double x1, double y1, double x2, double y2, int argb) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        GL11.glLineWidth(1.5f);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        bb.pos(x1, y1, 0).endVertex();
        bb.pos(x2, y2, 0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }


    // ------------------------------------------------------------------ resizing a quest

    /** The quest being resized (a dot at its corner is dragged), its size before, and whether the dot is held. */
    private String resizeQuest = "";
    private double resizeBefore;
    private boolean resizeGrab;

    private void startResize(Quest q) {
        resizeQuest = q.id;
        resizeBefore = q.size;
        resizeGrab = false;
        selectedQuest = "";
    }

    private static String sizeText(double v) {
        return new java.math.BigDecimal(v).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private void endResize(boolean accept) {
        QuestBook book = ClientQuests.book();
        Quest q = book == null ? null : book.quest(resizeQuest);
        if (q != null) {
            double now = q.size;
            q.size = resizeBefore;
            if (accept && now != resizeBefore) {
                q.size = now;
                send("set_prop", args("id", q.id, "key", "size", "value", String.valueOf(now)));
            }
        }
        resizeQuest = "";
        resizeGrab = false;
    }

    /** The dot at the lower right corner of the quest, and the box at the top right that says what the size is. */
    private void drawResize(FontRenderer fr, QuestBook book, ViewTransform v, double zoom) {
        Quest q = book.quest(resizeQuest);
        if (q == null) {
            resizeQuest = "";
            return;
        }
        double cx = v.screenX(q.x * GRID);
        double cy = v.screenY(q.y * GRID);
        if (resizeGrab && Mouse.isButtonDown(0)) {
            double base = NODE * zoom;
            double size = 2 * Math.max(lastMouseX - cx, lastMouseY - cy) / base;
            if (!GuiScreen.isCtrlKeyDown()) {
                size = Math.round(size * 16.0) / 16.0; // snaps to sixteenths unless Ctrl is held
            }
            q.size = Math.max(0.0625, Math.min(64.0, size));
        }
        double half = NODE * q.size * zoom / 2;
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/marker.png", cx + half - 8, cy + half - 8, 16, 16, 0xFFFFFFFF)) {
            rect((int) (cx + half) - 3, (int) (cy + half) - 3, 7, 7, 0xFF66CCFF);
        }
        String l1 = "Size: " + sizeText(q.size) + " x " + sizeText(q.size);
        String l2 = "Ctrl: disable snapping (1/16 steps)";
        String l3 = "Esc: cancel";
        int w = Math.max(fr.getStringWidth(l1), Math.max(fr.getStringWidth(l2), fr.getStringWidth(l3))) + 12;
        int bx = bounds.x() + bounds.width() - w - 34; // left of the column of buttons
        int by = bounds.y() + 6;
        Gui.drawRect(bx, by, bx + w, by + 38, 0xE0101010);
        fr.drawStringWithShadow(l1, bx + 6, by + 4, 0xFFFFFF);
        fr.drawStringWithShadow(l2, bx + 6, by + 15, GuiScreen.isCtrlKeyDown() ? 0xFFFF55 : 0xFFFFFF);
        fr.drawStringWithShadow(l3, bx + 6, by + 26, 0xFFFFFF);
    }

    private boolean overResizeDot(ViewTransform v, int mouseX, int mouseY) {
        QuestBook book = ClientQuests.book();
        Quest q = book == null ? null : book.quest(resizeQuest);
        if (q == null) {
            return false;
        }
        double half = NODE * com.levviata.reflibquests.model.Presets.size(ClientQuests.book(), q) * v.zoom() / 2;
        double hx = v.screenX(q.x * GRID) + half;
        double hy = v.screenY(q.y * GRID) + half;
        return Math.abs(mouseX - hx) <= 9 && Math.abs(mouseY - hy) <= 9;
    }

    private static float questPulse() {
        float t = (System.currentTimeMillis() % 2000L) / 2000f;
        return t < 0.5f ? t * 2f : 2f - t * 2f;
    }

    /** Mix a colour towards white by {@code amount} (0 keeps it, 1 is white); alpha is kept. */
    private static int lighten(int argb, float amount) {
        int r = (argb >> 16 & 255) + Math.round((255 - (argb >> 16 & 255)) * amount);
        int g = (argb >> 8 & 255) + Math.round((255 - (argb >> 8 & 255)) * amount);
        int b = (argb & 255) + Math.round((255 - (argb & 255)) * amount);
        return argb & 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    private static String hoverQuest = "";
    private static String hoverImage = "";
    private static long hoverImageSince;
    private static String movingImage = "";
    private static double movingOrigX;
    private static double movingOrigY;

    /** Screen geometry of a picture: centre x, centre y, width, height, rotation; null if it is not drawn. */
    private double[] imageGeo(Definitions.ChapterImage img, ViewTransform v) {
        DataNode.Compound raw = img.raw();
        String tex = raw.getString("image", "");
        if (tex.isEmpty() || raw.getBool("dev", false) && !isEditing()) {
            return null;
        }
        net.minecraft.util.ResourceLocation loc;
        try {
            loc = new net.minecraft.util.ResourceLocation(tex);
        } catch (RuntimeException ex) {
            return null;
        }
        if (!DescriptionView.textureExists(mc(), loc) && paintingArt(tex) == null) {
            return null;
        }
        return new double[] {v.screenX(raw.getDouble("x", 0) * GRID), v.screenY(raw.getDouble("y", 0) * GRID),
            raw.getDouble("width", 1) * GRID * IMAGE_SCALE * v.zoom(), raw.getDouble("height", 1) * GRID * IMAGE_SCALE * v.zoom(),
            raw.getDouble("rotation", 0)};
    }

    /**
     * FTB Quests 26.1.2 can name a vanilla painting as a chapter picture ({@code minecraft:backyard}). This game keeps
     * its paintings in one sheet, so the name is looked up among the painting types of the sheet.
     */
    @javax.annotation.Nullable
    private static net.minecraft.entity.item.EntityPainting.EnumArt paintingArt(String tex) {
        int colon = tex.indexOf(':');
        String name = colon < 0 ? tex : tex.substring(colon + 1);
        if (name.indexOf('/') >= 0 || name.indexOf('.') >= 0) {
            return null;
        }
        for (net.minecraft.entity.item.EntityPainting.EnumArt art : net.minecraft.entity.item.EntityPainting.EnumArt.values()) {
            if (art.title.equalsIgnoreCase(name.replace("_", ""))) {
                return art;
            }
        }
        return null;
    }

    private static final net.minecraft.util.ResourceLocation PAINTING_SHEET =
            new net.minecraft.util.ResourceLocation("textures/painting/paintings_kristoffer_zetterstrand.png");

    /** The topmost picture under the mouse, or null. */
    private Definitions.ChapterImage imageAt(Chapter chapter, ViewTransform v, int mx, int my) {
        List<Definitions.ChapterImage> list = new ArrayList<>(chapter.images);
        list.sort(java.util.Comparator.comparingInt(i -> i.raw().getInt("order", 0)));
        for (int i = list.size() - 1; i >= 0; i--) {
            double[] g = imageGeo(list.get(i), v);
            if (g == null) {
                continue;
            }
            double a = Math.toRadians(g[4]);
            double dx = mx - g[0];
            double dy = my - g[1];
            double lx = dx * Math.cos(a) + dy * Math.sin(a);
            double ly = -dx * Math.sin(a) + dy * Math.cos(a);
            if (Math.abs(lx) <= g[2] / 2 && Math.abs(ly) <= g[3] / 2) {
                return list.get(i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ pictures: the menu, resizing and rotating

    /** What a right click on a picture offers in edit mode. */
    private void imageMenu(Definitions.ChapterImage target) {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(target.raw().getString("image", "?"), "", "", false, null));
        items.add(new MenuItem("Edit", "", "settings", true, () -> mc().displayGuiScreen(new ImagePropertiesScreen(mc().currentScreen, target))));
        items.add(new MenuItem("Move", "", "lib:up", false, () -> {
            if (target.raw().getBool("position_locked", false)) {
                notice("This image's position is locked");
                return;
            }
            movingImage = target.id();
            movingOrigX = target.raw().getDouble("x", 0);
            movingOrigY = target.raw().getDouble("y", 0);
        }));
        items.add(new MenuItem("Resize", "", "lib:expand", false, () -> startImageMode("resize", target)));
        items.add(new MenuItem("Rotate", "", "lib:refresh", false, () -> startImageMode("rotate", target)));
        items.add(new MenuItem("Copy ID", "", "lib:info", false, () -> GuiScreen.setClipboardString(target.id())));
        items.add(new MenuItem("Delete", "", "lib:remove", false, () -> send("delete_image", args("id", target.id()))));
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    private String imgMode = "";
    private String imgModeId = "";
    private Definitions.ChapterImage imgBefore;
    private boolean imgGrab;

    private void startImageMode(String mode, Definitions.ChapterImage img) {
        imgMode = mode;
        imgModeId = img.id();
        imgBefore = img;
        imgGrab = false;
    }

    private Definitions.ChapterImage imageById(Chapter c, String id) {
        for (Definitions.ChapterImage i : c.images) {
            if (i.id().equals(id)) {
                return i;
            }
        }
        return null;
    }

    private void replaceImageRaw(Chapter c, String id, DataNode.Compound raw) {
        for (int i = 0; i < c.images.size(); i++) {
            if (c.images.get(i).id().equals(id)) {
                c.images.set(i, new Definitions.ChapterImage(id, raw));
                return;
            }
        }
    }

    private void endImageMode(boolean accept) {
        QuestBook book = ClientQuests.book();
        Chapter c = book == null ? null : currentChapter(book);
        Definitions.ChapterImage now = c == null ? null : imageById(c, imgModeId);
        if (c != null && now != null && imgBefore != null) {
            if (accept) {
                DataNode.Compound props = DataNode.Compound.empty();
                for (String key : List.of("width", "height", "rotation")) {
                    double before = imgBefore.raw().getDouble(key, key.equals("rotation") ? 0 : 1);
                    double after = now.raw().getDouble(key, before);
                    if (before != after) {
                        props = props.with(key, after);
                    }
                }
                if (!props.entries().isEmpty()) {
                    send("set_image_props", args("id", imgModeId).with("props", props));
                }
            } else {
                replaceImageRaw(c, imgModeId, imgBefore.raw());
            }
        }
        imgMode = "";
        imgModeId = "";
        imgBefore = null;
        imgGrab = false;
    }

    private static String degrees(double d) {
        return new java.math.BigDecimal(d).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** The handle of the mode: the lower right corner when resizing, a point above the picture when rotating. */
    private double[] imageHandle(Definitions.ChapterImage img, ViewTransform v) {
        DataNode.Compound raw = img.raw();
        double cx = v.screenX(raw.getDouble("x", 0) * GRID);
        double cy = v.screenY(raw.getDouble("y", 0) * GRID);
        double w = raw.getDouble("width", 1) * GRID * IMAGE_SCALE * v.zoom();
        double h = raw.getDouble("height", 1) * GRID * IMAGE_SCALE * v.zoom();
        if (imgMode.equals("resize")) {
            return new double[] {cx + w / 2, cy + h / 2};
        }
        double a = Math.toRadians(raw.getDouble("rotation", 0));
        double r = h / 2 + 14;
        return new double[] {cx + Math.sin(a) * r, cy - Math.cos(a) * r};
    }

    /** Updates the picture while the handle is held, draws the handle and the hint box. */
    private void drawImageMode(FontRenderer fr, QuestBook book, ViewTransform v, Chapter chapter) {
        Definitions.ChapterImage img = imageById(chapter, imgModeId);
        if (img == null) {
            imgMode = "";
            return;
        }
        DataNode.Compound raw = img.raw();
        double cx = v.screenX(raw.getDouble("x", 0) * GRID);
        double cy = v.screenY(raw.getDouble("y", 0) * GRID);
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        if (imgGrab && Mouse.isButtonDown(0)) {
            if (imgMode.equals("resize")) {
                double unit = GRID * IMAGE_SCALE * v.zoom();
                double nw = 2 * (lastMouseX - cx) / unit;
                double nh = 2 * (lastMouseY - cy) / unit;
                if (!ctrl) {
                    nw = Math.round(nw * 16.0) / 16.0;
                    nh = Math.round(nh * 16.0) / 16.0;
                }
                replaceImageRaw(chapter, imgModeId, raw.with("width", Math.max(0.0625, Math.min(200, nw))).with("height", Math.max(0.0625, Math.min(200, nh))));
            } else {
                double angle = Math.toDegrees(Math.atan2(lastMouseY - cy, lastMouseX - cx)) + 90;
                if (!ctrl) {
                    angle = Math.round(angle / 15.0) * 15.0; // snaps to 15 degree steps
                }
                angle = ((angle % 360) + 360) % 360;
                replaceImageRaw(chapter, imgModeId, raw.with("rotation", angle));
            }
            img = imageById(chapter, imgModeId);
            raw = img.raw();
        }
        double[] handle = imageHandle(img, v);
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/marker.png", handle[0] - 8, handle[1] - 8, 16, 16, 0xFFFFFFFF)) {
            rect((int) handle[0] - 3, (int) handle[1] - 3, 7, 7, 0xFF66CCFF);
        }
        String l1 = imgMode.equals("resize")
                ? "Size: " + degrees(raw.getDouble("width", 1)) + " x " + degrees(raw.getDouble("height", 1))
                : "Rotation: " + degrees(raw.getDouble("rotation", 0)) + "\u00b0";
        String l2 = imgMode.equals("resize") ? "Ctrl: disable snapping (1/16 steps)" : "Ctrl: disable snapping (15\u00b0 steps)";
        int w = Math.max(fr.getStringWidth(l1), Math.max(fr.getStringWidth(l2), fr.getStringWidth("Esc: cancel"))) + 12;
        int bx = bounds.x() + bounds.width() - w - 34;
        int by = bounds.y() + 6;
        Gui.drawRect(bx, by, bx + w, by + 38, 0xE0101010);
        fr.drawStringWithShadow(l1, bx + 6, by + 4, 0xFFFFFF);
        fr.drawStringWithShadow(l2, bx + 6, by + 15, ctrl ? 0xFFFF55 : 0xFFFFFF);
        fr.drawStringWithShadow("Esc: cancel", bx + 6, by + 26, 0xFFFFFF);
    }

    private void replaceImage(Chapter chapter, String id, double x, double y) {
        for (int i = 0; i < chapter.images.size(); i++) {
            Definitions.ChapterImage img = chapter.images.get(i);
            if (img.id().equals(id)) {
                chapter.images.set(i, new Definitions.ChapterImage(id, img.raw().with("x", x).with("y", y)));
                return;
            }
        }
    }

    /** While a picture is being moved it follows the mouse (in steps of a quarter map unit). */
    private void followMovingImage(Chapter chapter) {
        if (movingImage.isEmpty()) {
            return;
        }
        double factor = new ScaledResolution(mc()).getScaleFactor();
        double px = Mouse.getX() / factor;
        double py = (mc().displayHeight - Mouse.getY()) / factor;
        ViewTransform v = viewFor(chapter, mapRect());
        replaceImage(chapter, movingImage, Math.round(v.worldX(px) / GRID * 4.0) / 4.0, Math.round(v.worldY(py) / GRID * 4.0) / 4.0);
    }

    private void dropImage() {
        QuestBook book = ClientQuests.book();
        Chapter c = book == null ? null : currentChapter(book);
        String id = movingImage;
        movingImage = "";
        if (c == null) {
            return;
        }
        for (Definitions.ChapterImage img : c.images) {
            if (img.id().equals(id)) {
                send("move_image", args("id", id, "x", img.raw().getDouble("x", 0), "y", img.raw().getDouble("y", 0)));
            }
        }
    }

    private void cancelMove() {
        QuestBook book = ClientQuests.book();
        Chapter c = book == null ? null : currentChapter(book);
        String id = movingImage;
        movingImage = "";
        if (c != null && !id.isEmpty()) {
            replaceImage(c, id, movingOrigX, movingOrigY);
        }
    }
    private static final Map<String, Boolean> CHAPTER_ALERT = new HashMap<>();
    private static boolean anyAlert;
    private static long alertRefreshAt;
    private static TeamProgress alertProgress;
    private static int alertVersion = -1;

    private static void refreshAlerts(QuestBook book, ProgressEngine engine, TeamProgress p, UUID me) {
        long now = System.currentTimeMillis();
        if (now < alertRefreshAt && alertProgress == p && alertVersion == ClientQuests.bookVersion()) {
            return;
        }
        alertRefreshAt = now + 300;
        alertProgress = p;
        alertVersion = ClientQuests.bookVersion();
        CHAPTER_ALERT.clear();
        anyAlert = false;
        for (Chapter c : book.chapters()) {
            boolean a = false;
            for (Quest q : c.quests) {
                if (claimable(engine, p, me, q)) {
                    a = true;
                    break;
                }
            }
            CHAPTER_ALERT.put(c.id, a);
            anyAlert |= a;
        }
    }
    private static final net.minecraft.util.ResourceLocation LADDER = new net.minecraft.util.ResourceLocation("textures/blocks/ladder.png");

    /** A line drawn with the side rail of the game's ladder texture, repeated along the line. */
    private static void ladderLine(double x1, double y1, double x2, double y2, double thickness, int argb, int flow) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) {
            return;
        }
        double nx = -dy / len * thickness / 2;
        double ny = dx / len * thickness / 2;
        boolean chevrons = QuestArt.hasGui("dependency"); // FTB Quests' own line: a band of chevrons pointing at the quest
        double repeats = len / (chevrons ? thickness : thickness * 8);
        double phase = (System.currentTimeMillis() % 20000L) / 20000.0 * 8.0; // slow drift: 0.4 tile per second
        double v0 = -flow * phase;
        double v1 = repeats - flow * phase;
        mc().getTextureManager().bindTexture(chevrons ? QuestArt.guiTexture("dependency") : LADDER);
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, 1f);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        if (chevrons) {
            bb.pos(x1 + nx, y1 + ny, 0).tex(v0, 0).endVertex();
            bb.pos(x2 + nx, y2 + ny, 0).tex(v1, 0).endVertex();
            bb.pos(x2 - nx, y2 - ny, 0).tex(v1, 1).endVertex();
            bb.pos(x1 - nx, y1 - ny, 0).tex(v0, 1).endVertex();
        } else {
            bb.pos(x1 + nx, y1 + ny, 0).tex(2 / 16.0, v0).endVertex();
            bb.pos(x2 + nx, y2 + ny, 0).tex(2 / 16.0, v1).endVertex();
            bb.pos(x2 - nx, y2 - ny, 0).tex(4 / 16.0, v1).endVertex();
            bb.pos(x1 - nx, y1 - ny, 0).tex(4 / 16.0, v0).endVertex();
        }
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableCull();
    }

    /** The pictures placed on a chapter's map (frames, title art): centred on x/y, sized and rotated as the pack says. */
    private void drawChapterImages(ViewTransform v, Chapter chapter, Rect map) {
        if (chapter.images.isEmpty()) {
            return;
        }
        List<Definitions.ChapterImage> list = new ArrayList<>(chapter.images);
        list.sort(java.util.Comparator.comparingInt(i -> i.raw().getInt("order", 0)));
        Minecraft mc = mc();
        for (Definitions.ChapterImage img : list) {
            DataNode.Compound raw = img.raw();
            String tex = raw.getString("image", "");
            if (tex.isEmpty() || raw.getBool("dev", false) && !isEditing()) {
                continue;
            }
            net.minecraft.util.ResourceLocation loc;
            try {
                loc = new net.minecraft.util.ResourceLocation(tex);
            } catch (RuntimeException ex) {
                continue;
            }
            double w = raw.getDouble("width", 1) * GRID * IMAGE_SCALE * v.zoom();
            double h = raw.getDouble("height", 1) * GRID * IMAGE_SCALE * v.zoom();
            double cx = v.screenX(raw.getDouble("x", 0) * GRID);
            double cy = v.screenY(raw.getDouble("y", 0) * GRID);
            double reach = Math.max(w, h) * 0.75;
            net.minecraft.entity.item.EntityPainting.EnumArt art = paintingArt(tex);
            if (cx + reach < map.x() || cx - reach > map.x() + map.width() || cy + reach < map.y() || cy - reach > map.y() + map.height()
                    || !DescriptionView.textureExists(mc, loc) && art == null) {
                continue;
            }
            int alpha = Math.max(0, Math.min(255, raw.getInt("alpha", 255)));
            if (art != null && !DescriptionView.textureExists(mc, loc)) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(cx, cy, 0);
                GlStateManager.rotate((float) raw.getDouble("rotation", 0), 0, 0, 1);
                mc.getTextureManager().bindTexture(PAINTING_SHEET);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GlStateManager.enableBlend();
                GlStateManager.color(1f, 1f, 1f, alpha / 255f);
                float u0 = art.offsetX / 256f;
                float v0 = art.offsetY / 256f;
                float u1 = (art.offsetX + art.sizeX) / 256f;
                float v1 = (art.offsetY + art.sizeY) / 256f;
                net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
                net.minecraft.client.renderer.BufferBuilder buf = tess.getBuffer();
                buf.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
                buf.pos(-w / 2, h / 2, 0).tex(u0, v1).endVertex();
                buf.pos(w / 2, h / 2, 0).tex(u1, v1).endVertex();
                buf.pos(w / 2, -h / 2, 0).tex(u1, v0).endVertex();
                buf.pos(-w / 2, -h / 2, 0).tex(u0, v0).endVertex();
                tess.draw();
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.popMatrix();
                continue;
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.rotate((float) raw.getDouble("rotation", 0), 0, 0, 1);
            // large pictures are scaled smoothly; small ones (sprites) stay sharp
            mc.getTextureManager().bindTexture(loc);
            boolean sprite = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) <= 128
                    && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) <= 128;
            if (sprite) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            }
            QuestArt.quad(loc, -w / 2, -h / 2, w, h, alpha << 24 | 0xFFFFFF, !sprite);
            GlStateManager.popMatrix();
            if (isEditing() && (img.id().equals(hoverImage) || img.id().equals(movingImage))) {
                // outline of the picture being pointed at or moved
                double a = Math.toRadians(raw.getDouble("rotation", 0));
                double cs = Math.cos(a);
                double sn = Math.sin(a);
                double[][] c = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
                for (int k = 0; k < 4; k++) {
                    double[] p1 = c[k];
                    double[] p2 = c[(k + 1) % 4];
                    slantedLine(cx + p1[0] * cs - p1[1] * sn, cy + p1[0] * sn + p1[1] * cs,
                            cx + p2[0] * cs - p2[1] * sn, cy + p2[0] * sn + p2[1] * cs, 2.0, img.id().equals(movingImage) ? 0xFF6BD36B : 0xFFFFFFFF);
                }
            }
        }
    }

    /** A line of any angle, drawn as one rotated rectangle. */
    private static void slantedLine(double x1, double y1, double x2, double y2, double thickness, int argb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) {
            return;
        }
        double nx = -dy / len * thickness / 2;
        double ny = dx / len * thickness / 2;
        GlStateManager.disableCull();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24 & 255) / 255f);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        bb.pos(x1 + nx, y1 + ny, 0).endVertex();
        bb.pos(x2 + nx, y2 + ny, 0).endVertex();
        bb.pos(x2 - nx, y2 - ny, 0).endVertex();
        bb.pos(x1 - nx, y1 - ny, 0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
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
        // the model sits 200 units deep, scaled: a big icon would leave the depth range and be cut, so it is never scaled in depth
        GlStateManager.scale(scale, scale, Math.min(scale, 1f));
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
        // everything behind an open quest is dimmed (measured from FTB Quests: about 63% of a dark grey)
        Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xA0202426);
        Ui.shadow(r);
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF0E0F12);
        Gui.drawRect(r.x() + 1, r.y() + 1, r.x() + r.width() - 1, r.y() + r.height() - 1, 0xF51F2228);
        if (!selectedQuest.equals(lastDetailQuest)) {
            lastDetailQuest = selectedQuest;
            detailScroll = 0;
            descPage = 0;
        }
        clip = r;
        int x = r.x() + 8;
        int w = r.width() - 16;
        final int top = r.y() + 8 - detailScroll;
        int y = top;
        boolean details = QuestRules.detailsVisible(book, p, q);
        State state = stateOf(book, engine, p, q);
        fr.drawStringWithShadow(fr.trimStringToWidth(QuestText.title(q), w - 34), x, y, 0xFFFFA0);
        drawPinButton(fr, q, r.x() + r.width() - 38, r.y() + 4);
        Rect closeBox = new Rect(r.x() + r.width() - 14, r.y() + 4, 10, 12);
        fr.drawStringWithShadow("x", closeBox.x() + 2, closeBox.y() + 2, 0xFF5555);
        hits.add(new Hit(closeBox, () -> selectedQuest = "", "Close"));
        y += 12;
        if (!q.subtitle.isEmpty() && details) {
            fr.drawStringWithShadow(fr.trimStringToWidth(RichText.toSectionCodes(q.subtitle), w), x, y, 0xAAAAAA);
            y += 12;
        }
        String stateText = state == State.DONE ? "Completed" : state == State.LOCKED ? "Locked: finish the quests before it"
                : state == State.ACTIVE ? "In progress " + QuestRules.percent(p, q) + "%" : "Available";
        fr.drawStringWithShadow(stateText, x, y, colorOf(state) | 0xFF000000);
        y += 14;
        if (!details) {
            fr.drawStringWithShadow("Details are hidden until this quest is available.", x, y, 0x888888);
            detailContentHeight = y + 12 - top;
            clip = null;
            return;
        }
        if (!q.description.isEmpty()) {
            DescriptionView.Layout lay = DescriptionView.layout(fr, q.description, descPage, w);
            if (lay.pages() > 1) {
                descPage = Math.max(0, Math.min(lay.pages() - 1, descPage));
                y = pageControls(fr, x, y, lay.pages());
            }
            final QuestBook shownBook = book;
            DescriptionView.draw(fr, lay, x, y, lastMouseX, lastMouseY, (box, click, hover) ->
                    addHit(new Rect(box[0], box[1], box[2], box[3]), click == null ? () -> { } : () -> onLink(shownBook, click),
                            !hover.isEmpty() ? hover : click.value()));
            y += lay.height() + 4;
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
        detailContentHeight = y + 8 - top;
        clip = null;
    }

    // ------------------------------------------------------------------ quest window (normal mode)

    private static String depPopup = "";
    private static String choiceFor = "";

    private static String dependantsOfQuest = "";
    private static int dependantsVersion = -1;
    private static List<Quest> dependantsCache = List.of();

    private static List<Quest> dependantsOf(QuestBook book, Quest q) {
        if (q.id.equals(dependantsOfQuest) && dependantsVersion == ClientQuests.bookVersion()) {
            return dependantsCache;
        }
        List<Quest> out = new ArrayList<>();
        for (Quest other : book.allQuests()) {
            if (other.dependencies.contains(q.id)) {
                out.add(other);
            }
        }
        dependantsOfQuest = q.id;
        dependantsVersion = ClientQuests.bookVersion();
        dependantsCache = out;
        return out;
    }

    /** Draws a quest's icon at a given pixel size: its item, else the "missing item" art, else a check for checkmark quests. */
    private void drawQuestIcon(Quest q, float x, float y, float size, boolean done) {
        // upstream rule: the quest's own icon, else the icon of its first task
        if (!q.icon.entries().isEmpty()) {
            ItemStack s = QuestText.stackOf(q.icon);
            if (s != null && !s.isEmpty()) {
                drawItem(s, x, y, size / 16f);
            } else {
                Ui.missingItem(x, y, size);
            }
            return;
        }
        if (q.tasks.isEmpty()) {
            return;
        }
        Task first = q.tasks.get(0);
        ItemStack named = QuestText.taskIcon(first);
        if (named != null && !first.type().equals("item")) {
            drawItem(named, x, y, size / 16f); // a task with an icon of its own, or a kill task's spawn egg
        } else if (first.type().equals("custom")) {
            Ui.icon(Ui.LIBRARY, "textures/icons/color_hsb.png", x, y, size, size, 0xFFFFFFFF);
        } else if (first.type().equals("observation")) {
            Ui.icon(Ui.LIBRARY, "textures/icons/art.png", x, y, size, size, 0xFFFFFFFF);
        } else if (first.type().equals("gamestage") && first.params().getCompound("icon").entries().isEmpty()) {
            Ui.icon(Ui.LIBRARY, "textures/icons/controller.png", x, y, size, size, 0xFFFFFFFF);
        } else if (first.type().equals("checkmark")) {
            Ui.mapCheck(x, y, size, done);
        } else if (first.type().equals("item")) {
            ItemStack s = QuestText.stackOf(first.params().get("item"));
            if (s != null && !s.isEmpty()) {
                drawItem(s, x, y, size / 16f);
            } else {
                Ui.missingItem(x, y, size);
            }
        } else {
            ItemStack s = QuestText.iconOf(q);
            if (s != null && !s.isEmpty()) {
                drawItem(s, x, y, size / 16f);
            }
        }
    }

    /** Stack size in the game's own style: white, with shadow, in the bottom-right corner of the 16x16 item at (x, y). */
    private static void countText(FontRenderer fr, String text, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 17 - fr.getStringWidth(text) * 0.62f, y + 17 - 8 * 0.62f, 300);
        GlStateManager.scale(0.62f, 0.62f, 1f);
        fr.drawStringWithShadow(text, 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    private void iconBox(FontRenderer fr, String label, int x, int y) {
        Ui.box(new Rect(x, y, 18, 18), 0xFF2E323B);
        fr.drawStringWithShadow(label, x + 9 - fr.getStringWidth(label) / 2, y + 5, 0xCCCCCC);
    }

    private void drawTaskIcon(FontRenderer fr, Task t, boolean done, int x, int y) {
        ItemStack named = t.type().equals("item") || t.type().equals("checkmark") ? null : QuestText.taskIcon(t);
        if (named != null) {
            drawItem(named, x + 1, y + 1, 1f);
            if (t.type().equals("kill") && t.maxProgress() > 1) {
                countText(fr, String.valueOf(t.maxProgress()), x + 1, y + 1); // FTB Quests puts the number under the egg
            } else if (t.type().equals("fluid")) {
                long mb = t.params().get("fluid") instanceof DataNode.Compound fc ? fc.getLong("amount", 1000) : 1000;
                countText(fr, mb % 1000 == 0 ? mb / 1000 + " B" : mb + " mB", x + 1, y + 1);
            }
        } else if (t.type().equals("custom")) {
            Ui.icon(Ui.LIBRARY, "textures/icons/color_hsb.png", x + 1, y + 1, 16, 16, 0xFFFFFFFF);
        } else if (t.type().equals("observation")) {
            Ui.icon(Ui.LIBRARY, "textures/icons/art.png", x + 1, y + 1, 16, 16, 0xFFFFFFFF);
        } else if (t.type().equals("gamestage") && t.params().getCompound("icon").entries().isEmpty()) {
            Ui.icon(Ui.LIBRARY, "textures/icons/controller.png", x + 1, y + 1, 16, 16, 0xFFFFFFFF);
        } else if (t.type().equals("checkmark")) {
            if (done ? !Ui.taskCheck(x + 1, y + 1, 16, true) : !Ui.questsGui("check_gray", x + 1, y + 1, 16, 16, 0xFFFFFFFF)) {
                iconBox(fr, "v", x, y);
            }
        } else if (t.type().equals("item")) {
            ItemStack s = QuestText.stackOf(t.params().get("item"));
            if (s == null) {
                if (!Ui.missingItem(x + 1, y + 1, 16)) {
                    iconBox(fr, "?", x, y);
                }
            } else {
                drawItem(s, x + 1, y + 1, 1f);
            }
            if (t.maxProgress() > 1) {
                countText(fr, String.valueOf(t.maxProgress()), x + 1, y + 1);
            }
        } else {
            iconBox(fr, t.type().isEmpty() ? "?" : t.type().substring(0, 1).toUpperCase(java.util.Locale.ROOT), x, y);
        }
    }

    /** The box FTB Quests draws behind a hovered task or reward: 20 pixels, a 1 pixel dark edge, 26% black inside. */
    private void slotHover(int x, int y) {
        if (lastMouseX >= x - 1 && lastMouseX < x + 19 && lastMouseY >= y - 1 && lastMouseY < y + 19 && !(!menu.isEmpty() && menuRect(menu).contains(lastMouseX, lastMouseY))) {
            Gui.drawRect(x - 1, y - 1, x + 19, y + 19, 0xFF1B1D1E);
            Gui.drawRect(x, y, x + 18, y + 18, 0x42000000);
        }
    }

    private void drawRewardIcon(FontRenderer fr, Reward rw, int x, int y) {
        DataNode.Compound pm = rw.params();
        switch (rw.type()) {
            case "item": {
                ItemStack s = QuestText.stackOf(pm.get("item"));
                if (s == null) {
                    if (!Ui.missingItem(x + 1, y + 1, 16)) {
                        iconBox(fr, "?", x, y);
                    }
                } else {
                    drawItem(s, x + 1, y + 1, 1f);
                }
                int count = (int) Math.min(Integer.MAX_VALUE, Task.itemCount(pm));
                if (count > 1) {
                    countText(fr, String.valueOf(count), x + 1, y + 1);
                }
                break;
            }
            case "xp":
            case "xp_levels":
                drawItem(new ItemStack(net.minecraft.init.Items.EXPERIENCE_BOTTLE), x + 1, y + 1, 1f);
                countText(fr, "+" + Math.max(1, pm.getInt(rw.type(), 1)), x + 1, y + 1);
                break;
            case "choice":
            case "random":
            case "loot":
            case "all_table": {
                // FTB Quests shows the picture of the table's first entry
                QuestBook qb = ClientQuests.book();
                com.levviata.reflibquests.model.Definitions.RewardTable table = qb == null ? null : qb.tables().get(com.levviata.reflibquests.io.Ids.tableId(pm));
                if (table != null && !table.entries.isEmpty() && !table.entries.get(0).reward().type().equals(rw.type())) {
                    Reward first = table.entries.get(0).reward();
                    if (first.type().equals("xp") || first.type().equals("xp_levels")) {
                        drawItem(new ItemStack(net.minecraft.init.Items.EXPERIENCE_BOTTLE), x + 1, y + 1, 1f); // no amount on a table's picture
                    } else {
                        drawRewardIcon(fr, first, x, y);
                    }
                } else {
                    drawItem(new ItemStack(net.minecraft.init.Blocks.CHEST), x + 1, y + 1, 1f);
                }
                break;
            }
            case "advancement":
                drawItem(pm.getString("advancement", "").endsWith("/root") ? new ItemStack(net.minecraft.init.Blocks.GRASS) : new ItemStack(net.minecraft.init.Items.WHEAT), x + 1, y + 1, 1f);
                break;
            case "toast":
                drawItem(new ItemStack(net.minecraft.init.Items.SIGN), x + 1, y + 1, 1f);
                break;
            default: {
                ItemStack own = pm.get("icon") instanceof DataNode.Compound ic && !ic.entries().isEmpty() ? QuestText.stackOf(ic) : null;
                if (own != null) {
                    drawItem(own, x + 1, y + 1, 1f);
                } else if (rw.type().equals("command")) {
                    drawItem(new ItemStack(net.minecraft.init.Blocks.COMMAND_BLOCK), x + 1, y + 1, 1f);
                } else if (rw.type().equals("gamestage") && Ui.icon(Ui.LIBRARY, "textures/icons/controller.png", x + 1, y + 1, 16, 16, 0xFFFFFFFF)) {
                    // the game controller, as FTB Quests draws a Stage reward (and as the Stage task)
                } else if (rw.type().equals("custom") && Ui.icon(Ui.LIBRARY, "textures/icons/color_hsb.png", x + 1, y + 1, 16, 16, 0xFFFFFFFF)) {
                    // the colour wheel, as FTB Quests draws a custom reward (and as the custom task above)
                } else {
                    iconBox(fr, rw.type().isEmpty() ? "?" : rw.type().substring(0, 1).toUpperCase(java.util.Locale.ROOT), x, y);
                }
            }
        }
    }

    /** The item's own tooltip (so tooltips added by other mods show), its mod's name, and the recipe hint when JEI is there. */
    private static String itemTip(ItemStack stack, boolean recipeHint) {
        return itemTip(stack, recipeHint, 1);
    }

    /** The item's tooltip; with a count above one the name line starts with it ("32x Sulfurous Stone"). */
    private static String itemTip(ItemStack stack, boolean recipeHint, int count) {
        return itemTip(stack, recipeHint, count, "");
    }

    /** As above, with {@code underName} (for example "0 / 32") on its own line right below the name. */
    private static String itemTip(ItemStack stack, boolean recipeHint, int count, String underName) {
        StringBuilder sb = new StringBuilder();
        List<String> lines = stack.getTooltip(mc().player, mc().gameSettings.advancedItemTooltips
                ? net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED : net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i == 0 && count > 1 ? "§f" + count + "x §r" + lines.get(i) : lines.get(i));
            if (i == 0 && !underName.isEmpty()) {
                sb.append('\n').append(underName);
            }
        }
        net.minecraft.util.ResourceLocation name = stack.getItem().getRegistryName();
        if (name != null) {
            net.minecraftforge.fml.common.ModContainer mod = net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get(name.getNamespace());
            String modName = mod != null ? mod.getName() : name.getNamespace().equals("minecraft") ? "Minecraft" : name.getNamespace();
            sb.append("\n§9§o").append(modName);
        }
        if (recipeHint && JeiBridge.available()) {
            sb.append("\n§eClick to view recipe(s)");
        }
        return sb.toString();
    }

    /** The contents of a reward table with each entry's chance, for the tooltip of choice / random / loot rewards. */
    private static String tableTip(QuestBook book, Reward rw) {
        RewardTable table = book.tables().get(QuestText.tableId(rw));
        if (table == null) {
            return QuestText.reward(rw);
        }
        double total = table.emptyWeight;
        for (WeightedReward wr : table.entries) {
            total += wr.weight();
        }
        StringBuilder sb = new StringBuilder();
        String head = table.title.isEmpty() ? QuestText.reward(rw) : RichText.toRainbowAware(table.title);
        sb.append(head);
        int shown = 0;
        for (WeightedReward wr : table.entries) {
            if (shown++ >= 14) {
                sb.append("\n§7+ ").append(table.entries.size() - 14).append(" more");
                break;
            }
            sb.append('\n').append(rewardLine(wr.reward())).append(" §7[")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", total <= 0 ? 0 : wr.weight() * 100.0 / total)).append("%]");
        }
        if (table.emptyWeight > 0 && total > 0) {
            sb.append("\n§7Nothing [").append(String.format(java.util.Locale.ROOT, "%.2f", table.emptyWeight * 100.0 / total)).append("%]");
        }
        return sb.toString();
    }

    /** One reward as a line: "4-8x Nether Quartz" for items (with their random bonus), the usual text otherwise. */
    private static String rewardLine(Reward rw) {
        if (rw.type().equals("item")) {
            int count = (int) Math.min(Integer.MAX_VALUE, Task.itemCount(rw.params()));
            int bonus = Math.max(0, rw.params().getInt("random_bonus", 0));
            String amount = bonus > 0 ? count + "-" + (count + bonus) + "x " : count > 1 ? count + "x " : "";
            return amount + QuestText.itemName(rw.params().get("item"));
        }
        if (rw.type().equals("xp") || rw.type().equals("xp_levels")) {
            return (rw.type().equals("xp") ? "" : "Levels: ") + "+" + Math.max(1, rw.params().getInt(rw.type(), 1)) + (rw.type().equals("xp") ? " XP" : "");
        }
        return QuestText.reward(rw);
    }

    // ---- the Edit menu and in-place editing of title, subtitle and text lines

    private static String inlineKind = "";
    private static String inlineQuest = "";
    private static TextEditor INLINE = new TextEditor("", 4000);
    private static int inlineScroll;

    private MenuItem[] editMenu(Quest q) {
        return new MenuItem[] {
            new MenuItem("Title", "[T]", "lib:notes", false, () -> startInline("title", q)),
            new MenuItem("Subtitle", "[S]", "lib:notes", false, () -> startInline("subtitle", q)),
            new MenuItem("Description", "[D]", "lib:notes", false, () -> mc().displayGuiScreen(new DescriptionEditorScreen(mc().currentScreen, q))),
            new MenuItem("Add Text Line", "[L]", "lib:notes", true, () -> startInline("line", q)),
            new MenuItem("Add Page Break", "[P]", "pagebreak", false, () -> appendPage(q)),
            new MenuItem("Add Image", "[I]", "lib:art", false, () -> mc().displayGuiScreen(
                    new InsertDialogs.ImageDialog(mc().currentScreen, tag -> appendLine(q, tag)))),
            new MenuItem("Quest Properties", "[Q]", "settings", true, () -> mc().displayGuiScreen(new QuestPropertiesScreen(mc().currentScreen, q)))
        };
    }

    private static void startInline(String kind, Quest q) {
        inlineKind = kind;
        inlineQuest = q.id;
        inlineScroll = 0;
        INLINE = new TextEditor(codesAsTyped(kind.equals("title") ? q.title : kind.equals("subtitle") ? q.subtitle : ""), 4000);
    }

    /** Legacy files write colour codes with the section sign, which the font would draw as colour and hide; the editor shows them as &6. */
    static String codesAsTyped(String text) {
        return text.replace('§', '&');
    }

    private static boolean inlineOn(String kind, Quest q) {
        return inlineKind.equals(kind) && q.id.equals(inlineQuest);
    }

    private static void sendText(Quest q, String title, String subtitle, List<String> lines) {
        List<DataNode> out = new ArrayList<>();
        for (String l : lines) {
            out.add(new DataNode.Str(l));
        }
        TableEdits.send("set_text", DataNode.Compound.empty().with("id", q.id).with("title", title)
                .with("subtitle", subtitle).with("desc", new DataNode.Lst(out)));
    }

    private static void addLocalLines(Quest q, List<String> add) {
        List<String> lines = new ArrayList<>(q.description);
        lines.addAll(add);
        q.description = lines; // the screen shows it at once; the server's copy arrives a moment later
        List<DataNode> out = new ArrayList<>();
        for (String l : add) {
            out.add(new DataNode.Str(l));
        }
        TableEdits.queue("add_lines", DataNode.Compound.empty().with("id", q.id).with("lines", new DataNode.Lst(out)));
    }

    private static void appendLine(Quest q, String line) {
        addLocalLines(q, List.of(line));
    }

    private static void commitInline() {
        QuestBook book = ClientQuests.book();
        Quest q = book == null ? null : book.quest(inlineQuest);
        String kind = inlineKind;
        String text = INLINE.text();
        inlineKind = "";
        if (q == null) {
            return;
        }
        if (kind.equals("title")) {
            q.title = text.strip();
            TableEdits.queue("set_title", DataNode.Compound.empty().with("id", q.id).with("text", text));
        } else if (kind.equals("subtitle")) {
            q.subtitle = text.strip();
            TableEdits.queue("set_subtitle", DataNode.Compound.empty().with("id", q.id).with("text", text));
        } else if (kind.equals("line") && !text.isEmpty()) {
            appendLine(q, text);
        }
    }

    /** A highlighted one-line text box drawn in place of the text it edits. */
    private void drawInlineField(FontRenderer fr, int x, int y, int w) {
        Gui.drawRect(x, y, x + w, y + 13, 0xFF4C7BC4);
        String text = INLINE.text();
        int caretX = fr.getStringWidth(text.substring(0, INLINE.caret()));
        int room = w - 10;
        // the text only moves when the caret would leave the box
        if (caretX - inlineScroll > room) {
            inlineScroll = caretX - room;
        } else if (caretX < inlineScroll) {
            inlineScroll = caretX;
        }
        int first = 0;
        while (first < text.length() && fr.getStringWidth(text.substring(0, first + 1)) <= inlineScroll) {
            first++;
        }
        String visible = fr.trimStringToWidth(text.substring(first), w - 8);
        int base = x + 4;
        if (INLINE.hasSelection()) {
            int s0 = Math.max(first, INLINE.selStart());
            int s1 = Math.min(first + visible.length(), INLINE.selEnd());
            if (s1 > s0) {
                int x0 = base + fr.getStringWidth(text.substring(first, s0));
                int x1 = base + fr.getStringWidth(text.substring(first, s1));
                Gui.drawRect(x0, y + 1, x1, y + 12, 0xAA3A6FD0);
            }
        }
        fr.drawStringWithShadow(visible, base, y + 3, 0xFFFFFF);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = base + fr.getStringWidth(text.substring(first, Math.max(first, INLINE.caret())));
            Gui.drawRect(cx, y + 2, cx + 1, y + 11, 0xFFFFFFFF);
        }
    }

    private void drawQuestWindow(FontRenderer fr, QuestBook book, ProgressEngine engine, TeamProgress p) {
        Quest q = book.quest(selectedQuest);
        Rect r = detailRect();
        if (!selectedQuest.equals(lastDetailQuest)) {
            lastDetailQuest = selectedQuest;
            detailScroll = 0;
            descPage = 0;
            depPopup = "";
            choiceFor = "";
        }
        final int right = r.x() + r.width();
        Gui.drawRect(r.x(), r.y(), right, r.y() + r.height(), 0xFF0E0F12);
        Gui.drawRect(r.x() + 1, r.y() + 1, right - 1, r.y() + r.height() - 1, 0xF5202329);
        boolean details = QuestRules.detailsVisible(book, p, q);
        UUID me = mc().player == null ? null : mc().player.getUniqueID();

        // ---- title bar: icon, centred title, pin, close
        final int bar = 20;
        Gui.drawRect(r.x() + 1, r.y() + 1, right - 1, r.y() + bar, 0xFF2A2D35);
        Gui.drawRect(r.x() + 1, r.y() + bar, right - 1, r.y() + bar + 1, 0xFF0E0F12);
        drawQuestIcon(q, r.x() + 5, r.y() + 3, 14, p.isCompleted(q.id));
        if (inlineOn("title", q)) {
            drawInlineField(fr, r.x() + 24, r.y() + 4, r.width() - 64);
        } else {
            String title = QuestText.titleRainbow(q);
            int maxTitle = r.width() - 64;
            int fullWidth = Ui.width(fr, title);
            if (fullWidth <= maxTitle) {
                Ui.drawText(fr, title, r.x() + r.width() / 2 - fullWidth / 2, r.y() + 7, 0xBBBBBB, maxTitle, false); // no shadow, like upstream
            } else {
                int diff = fullWidth - maxTitle;
                double travel = diff / 18.0;                       // seconds to cross at 18 px/s
                double cycle = 2 * (travel + 1.6);
                double t = (System.currentTimeMillis() / 1000.0) % cycle;
                double leg = t < travel + 1.6 ? t : cycle - t;     // there and back
                double frac = Math.max(0, Math.min(1, (leg - 0.8) / travel));
                scissor(new Rect(r.x() + 24, r.y() + 1, maxTitle, 18));
                Ui.drawText(fr, title, r.x() + 24 - (int) Math.round(diff * frac), r.y() + 7, 0xBBBBBB, Integer.MAX_VALUE, false);
                scissor(detailRect());
            }
        }
        boolean pinned = ClientQuests.pins().contains(q.id);
        Rect pinBox = new Rect(right - 28, r.y() + 5, 10, 10);
        if (!Ui.questsGui("pin", pinBox.x(), pinBox.y(), 10, 10, pinned ? 0xFFF8BD1C : 0xFFDBDBDB)) {
            fr.drawStringWithShadow("P", pinBox.x() + 2, pinBox.y() + 1, pinned ? 0xF8BD1C : 0xDBDBDB);
        }
        final String qid = q.id;
        hits.add(new Hit(pinBox, () -> QuestNet.togglePin(qid), pinned ? "Stop tracking" : "Track this quest on screen"));
        Rect closeBox = new Rect(right - 15, r.y() + 5, 10, 10);
        if (!Ui.questsGui("close", closeBox.x(), closeBox.y(), 10, 10, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("x", closeBox.x() + 2, closeBox.y() + 1, 0xFF5555);
        }
        hits.add(new Hit(closeBox, () -> selectedQuest = "", "Close"));

        // ---- scrolling content below the bar
        Rect content = new Rect(r.x() + 1, r.y() + bar + 1, r.width() - 2, r.height() - bar - 2);
        scissor(content);
        clip = content;
        final int top = content.y() - detailScroll;
        int y = top;

        // ---- Tasks | Rewards section
        int taskSlots = q.tasks.size() + (isEditing() ? 1 : 0);
        int rewardSlots = q.rewards.size() + (isEditing() ? 1 : 0);
        // FTB Quests 26.1.2 fills a column with at most 4 icons a row and balances the rows: 4 stay in one row, 5 become 3 + 2
        int taskPer = balancedPerRow(taskSlots);
        int rewardPer = balancedPerRow(rewardSlots);
        int taskRows = (taskSlots + taskPer - 1) / taskPer;
        int rewardRows = (rewardSlots + rewardPer - 1) / rewardPer;
        int rows = Math.max(1, Math.max(taskRows, rewardRows));
        int secH = 18 + rows * 21 + 4;
        int mid = r.x() + r.width() / 2;
        Gui.drawRect(r.x() + 1, y, right - 1, y + secH, 0xFF252932);
        Gui.drawRect(mid, y, mid + 1, y + secH, 0xFF0E0F12);
        Gui.drawRect(r.x() + 1, y + secH, right - 1, y + secH + 1, 0xFF0E0F12);
        boolean hasDeps = !q.dependencies.isEmpty();
        List<Quest> dependants = dependantsOf(book, q);
        Rect left = new Rect(r.x() + 1, y + 3, 16, 16);
        // the arrows as FTB Quests 26.1.2 draws them (measured at GUI scale 3: the 16 px texture shows at about 13 GUI px);
        // the click area fits the arrow with 4 px to spare
        double arrow = 13;
        int leeway = 4;
        double leftX = left.x() + (16 - arrow) / 2;
        double leftY = left.y() + (16 - arrow) / 2;
        // a locked quest: the dependencies arrow slides between white and dark red; with nothing to show an arrow is dark
        int leftTint = !hasDeps ? 0xFF1B1D1E : 0xFFFFFFFF;
        if (hasDeps && !ClientQuests.progress().isCompleted(q.id) && !QuestRules.dependenciesMet(book, ClientQuests.progress(), q)) {
            leftTint = mixColour(0xFFFFFFFF, 0xFFAA0000, (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 400.0)));
        }
        if (!Ui.questsGui("arrow_left", leftX, leftY, arrow, arrow, leftTint)) {
            fr.drawString("<", left.x() + 5, left.y() + 4, hasDeps ? 0xFFFFFF : 0x555555);
        }
        Rect rightArrow = new Rect(right - 17, y + 3, 16, 16);
        double rightX = rightArrow.x() + (16 - arrow) / 2;
        double rightY = rightArrow.y() + (16 - arrow) / 2;
        if (!Ui.questsGui("arrow_right", rightX, rightY, arrow, arrow, !dependants.isEmpty() ? 0xFFFFFFFF : 0xFF1B1D1E)) {
            fr.drawString(">", rightArrow.x() + 5, rightArrow.y() + 4, !dependants.isEmpty() ? 0xFFFFFF : 0x555555);
        }
        int hitSize = (int) Math.ceil(arrow) + 2 * leeway;
        Rect leftHit = new Rect((int) Math.round(leftX) - leeway, (int) Math.round(leftY) - leeway, hitSize, hitSize);
        Rect rightHit = new Rect((int) Math.round(rightX) - leeway, (int) Math.round(rightY) - leeway, hitSize, hitSize);
        hits.add(new Hit(leftHit, () -> depPopup = hasDeps ? (depPopup.equals("deps") ? "" : "deps") : "", hasDeps ? "View Dependencies" : "No Dependencies"));
        hits.add(new Hit(rightHit, () -> depPopup = !dependants.isEmpty() ? (depPopup.equals("dependants") ? "" : "dependants") : "", !dependants.isEmpty() ? "View Dependants" : "No Dependants"));
        String th = "Tasks";
        String rh = "Rewards";
        fr.drawStringWithShadow(th, r.x() + r.width() / 4 - fr.getStringWidth(th) / 2, y + 5, 0x6B6BFF);
        fr.drawStringWithShadow(rh, mid + r.width() / 4 - fr.getStringWidth(rh) / 2, y + 5, 0xFFAA00);
        boolean canWork = !p.isCompleted(q.id) && QuestRules.canProgress(book, p, q) && QuestRules.dependenciesMet(book, p, q);
        for (int i = 0; i < q.tasks.size(); i++) {
            Task t = q.tasks.get(i);
            int inRow = Math.min(taskPer, taskSlots - (i / taskPer) * taskPer);
            int sx = r.x() + r.width() / 4 - (inRow * 20 - 2) / 2 + (i % taskPer) * 20;
            int sy = y + 18 + (i / taskPer) * 21;
            boolean done = QuestRules.taskDone(p, t);
            slotHover(sx, sy);
            drawTaskIcon(fr, t, done, sx, sy);
            if (done && !t.type().equals("checkmark")) {
                Ui.check(sx + 10, sy - 3, 9, 0xFFFFFFFF);
            }
            StringBuilder tip = new StringBuilder();
            Runnable act = () -> { };
            // "0 / 32" in green under the name of an item task that needs several
            String progressLine = t.maxProgress() > 1 ? "§2" + Math.min(p.taskProgress(t.id()), t.maxProgress()) + " / " + t.maxProgress() : "";
            if (t.type().equals("item")) {
                DataNode spec = t.params().get("item");
                List<com.levviata.reflibquests.game.ItemResolver.Resolved> set = com.levviata.reflibquests.game.ItemSet.resolveAll(spec, ClientQuests.idMap());
                boolean collection = com.levviata.reflibquests.game.ItemSet.isFilter(spec) || set.size() > 1;
                boolean hand = !done && canWork && consumes(book, t);
                if (collection) {
                    String name = t.params().getString("title", "");
                    if (name.isEmpty()) {
                        name = com.levviata.reflibquests.game.ItemSet.suggestedName(spec);
                    }
                    tip.append(name.isEmpty() ? "Any of these items" : RichText.toRainbowAware(name));
                    if (!progressLine.isEmpty()) {
                        tip.append('\n').append(progressLine);
                    }
                    tip.append("\n§eClick to view valid items");
                    final List<ItemStack> shown = new ArrayList<>();
                    for (com.levviata.reflibquests.game.ItemResolver.Resolved res : set) {
                        shown.add(res.toStack(1));
                    }
                    final String taskName = name.isEmpty() ? QuestText.title(q) : RichText.toRainbowAware(name);
                    final boolean submit = hand;
                    final String tid = t.id();
                    act = () -> mc().displayGuiScreen(new ValidItemsScreen(mc().currentScreen, taskName, shown, tid, submit));
                } else {
                    ItemStack one = QuestText.stackOf(spec);
                    tip.append(one == null ? QuestText.task(t) : itemTip(one, jeiEnabled(book, q), (int) Math.min(Integer.MAX_VALUE, t.maxProgress()), progressLine));
                    if (hand) {
                        tip.append("\n§7Click to hand in items");
                        act = () -> QuestNet.submit(t.id());
                    } else if (one != null && jeiEnabled(book, q)) {
                        final ItemStack recipeOf = one;
                        act = () -> JeiBridge.showRecipes(recipeOf);
                    }
                }
            } else if (t.type().equals("xp")) {
                boolean points = t.params().getBool("points", false);
                long xpLeft = t.maxProgress() - p.taskProgress(t.id());
                long have = mc().player == null ? 0 : points ? mc().player.experienceTotal : mc().player.experienceLevel;
                tip.append("§f").append(points ? "XP: " : "XP Levels: ").append(have >= xpLeft ? "§a" : "§c").append(t.maxProgress());
                tip.append("\n§a").append(Math.min(p.taskProgress(t.id()), t.maxProgress())).append(" / ").append(t.maxProgress());
                if (!done && canWork) {
                    tip.append("\n\n§e§nClick to submit");
                    act = () -> QuestNet.submit(t.id());
                }
            } else if (t.type().equals("fluid")) {
                long mb = t.maxProgress();
                String amount = mb % 1000 == 0 ? mb / 1000 + " B" : mb + " mB";
                String fname = com.levviata.reflibquests.game.TaskTrackerNames.fluidLabel(t.params());
                long got = Math.min(p.taskProgress(t.id()), mb);
                tip.append("§f").append(amount).append(" of ").append(fname);
                tip.append("\n§a").append(got).append(" mB / ").append(amount).append(" [").append(mb == 0 ? 0 : got * 100 / mb).append("%]");
                if (!done && canWork) {
                    tip.append("\n\n§e§nClick to submit");
                    act = () -> QuestNet.submit(t.id());
                }
            } else if (t.type().equals("forge_energy")) {
                tip.append("§fForge Energy: §a").append(t.maxProgress()).append(" FE");
                if (!done && canWork) {
                    tip.append("\n\n§e§nClick to submit");
                    act = () -> QuestNet.submit(t.id());
                }
            } else if (t.type().equals("dimension")) {
                tip.append("§fVisit Dimension: §a").append(t.params().getString("dimension", "?")); // FTB: the dimension's id in green
            } else {
                tip.append(QuestText.task(t));
                if (!done && canWork && t.type().equals("checkmark")) {
                    tip.append("\n§eClick to confirm");
                    act = () -> QuestNet.check(t.id());
                }
            }
            if (t.optionalTask()) {
                tip.append("\n§7Optional Task");
            }
            if (t.type().equals("item") && t.params().getBool("task_screen_only", false)) {
                tip.append("\n\n§e§nItems can ONLY be submitted via a Task Screen!");
            }
            if (t.maxProgress() > 1 && !t.type().equals("item") && !t.type().equals("fluid")) { // item and fluid tasks already have it, in green under the name
                tip.append("\n§7").append(Math.min(p.taskProgress(t.id()), t.maxProgress())).append(" / ").append(t.maxProgress());
            }
            final String taskId = t.id();
            final Definitions.Task taskRef = t;
            addHitR(new Rect(sx, sy, 18, 18), act, tip.toString(), !isEditing() ? null
                    : () -> taskMenu(q, taskRef));
        }
        if (isEditing()) {
            int i = q.tasks.size();
            int inRow = Math.min(taskPer, taskSlots - (i / taskPer) * taskPer);
            int sx = r.x() + r.width() / 4 - (inRow * 20 - 2) / 2 + (i % taskPer) * 20;
            int sy = y + 18 + (i / taskPer) * 21;
            Rect plus = new Rect(sx, sy, 18, 18);
            if (!Ui.icon(Ui.LIBRARY, "textures/icons/add.png", sx + 1, sy + 1, 16, 16, 0xFFFFFFFF)) {
                iconBox(fr, "+", sx, sy);
            }
            final String owner = q.id;
            addHit(plus, () -> openMenu(lastMouseX, lastMouseY, taskTypeMenu(owner)),
                    "Add a task");
        }
        for (int i = 0; i < q.rewards.size(); i++) {
            Reward rw = q.rewards.get(i);
            int inRow = Math.min(rewardPer, rewardSlots - (i / rewardPer) * rewardPer);
            int sx = mid + r.width() / 4 - (inRow * 20 - 2) / 2 + (i % rewardPer) * 20;
            int sy = y + 18 + (i / rewardPer) * 21;
            boolean claimed = me != null && p.isClaimed(rw.id(), me);
            boolean ready = me != null && engine.canClaim(p, me, rw);
            slotHover(sx, sy);
            drawRewardIcon(fr, rw, sx, sy);
            if (claimed) {
                Ui.check(sx + 9, sy - 3, 10, 0xFFFFFFFF);
            } else if (ready) {
                Ui.alert(sx + 11, sy - 3, 8);
            }
            String tip;
            ItemStack rewardStack = rw.type().equals("item") ? QuestText.stackOf(rw.params().get("item")) : null;
            if (rw.type().equals("xp") || rw.type().equals("xp_levels")) {
                tip = "\u00a79" + (rw.type().equals("xp") ? "XP: " : "XP Levels: ") + "\u00a7a+" + Math.max(1, rw.params().getInt(rw.type(), 1));
            } else if (rewardStack != null) {
                tip = itemTip(rewardStack, jeiEnabled(book, q));
            } else if (rw.type().equals("choice") || rw.type().equals("random") || rw.type().equals("loot") || rw.type().equals("all_table")) {
                tip = tableTip(book, rw);
            } else if (rw.type().equals("custom") && rw.params().getString("title", "").isEmpty()) {
                tip = "§9Custom";
            } else {
                tip = QuestText.reward(rw);
            }
            tip += claimed ? "\n§aClaimed" : ready ? "\n§eClick to claim" : "";
            final Reward target = rw;
            final ItemStack recipeOf = rewardStack;
            Runnable act = ready ? (rw.type().equals("choice") ? () -> choiceFor = choiceFor.equals(target.id()) ? "" : target.id()
                    : () -> QuestNet.claim(target.id(), -1))
                    : recipeOf != null && jeiEnabled(book, q) ? () -> JeiBridge.showRecipes(recipeOf) : () -> { };
            final String rewardId = rw.id();
            addHitR(new Rect(sx, sy, 18, 18), act, tip, !isEditing() ? null
                    : () -> rewardMenu(q, target));
        }
        if (isEditing()) {
            int i = q.rewards.size();
            int inRow = Math.min(rewardPer, rewardSlots - (i / rewardPer) * rewardPer);
            int sx = mid + r.width() / 4 - (inRow * 20 - 2) / 2 + (i % rewardPer) * 20;
            int sy = y + 18 + (i / rewardPer) * 21;
            Rect plus = new Rect(sx, sy, 18, 18);
            if (!Ui.icon(Ui.LIBRARY, "textures/icons/add.png", sx + 1, sy + 1, 16, 16, 0xFFFFFFFF)) {
                iconBox(fr, "+", sx, sy);
            }
            final String owner = q.id;
            addHit(plus, () -> openMenu(lastMouseX, lastMouseY, rewardTypeMenu(owner)),
                    "Add a reward");
        }
        y += secH + 1;

        // ---- body: subtitle, description, choices
        int pages = 1;
        if (!details) {
            fr.drawStringWithShadow("Details are hidden until this quest is available.", r.x() + 6, y + 6, 0x888888);
            y += 20;
        } else {
            String subText = !q.subtitle.isEmpty() ? RichText.toRainbowAware(q.subtitle)
                    : isEditing() ? "[No Subtitle]" : "";
            if (inlineOn("subtitle", q)) {
                drawInlineField(fr, r.x() + 6, y + 3, r.width() - 12);
                y += 18;
            } else if (!subText.isEmpty()) {
                String sub = "§o" + subText;
                int sw = Math.min(Ui.width(fr, sub), r.width() - 12);
                Ui.drawText(fr, sub, r.x() + r.width() / 2 - sw / 2, y + 4, 0xAAAAAA, r.width() - 12);
                y += 16;
            }
            boolean textHidden = !isEditing() && !p.isCompleted(q.id) && textHiddenRule(book, q);
            if (!q.description.isEmpty() && !textHidden) {
                y += 4;
                int dw = r.width() - 14;
                DescriptionView.Layout lay = DescriptionView.layout(fr, q.description, descPage, dw);
                pages = lay.pages();
                descPage = Math.max(0, Math.min(pages - 1, descPage));
                final QuestBook shownBook = book;
                DescriptionView.draw(fr, lay, r.x() + 7, y, lastMouseX, lastMouseY, (box, click, hover) ->
                        addHit(new Rect(box[0], box[1], box[2], box[3]), click == null ? () -> { } : () -> onLink(shownBook, click),
                                !hover.isEmpty() ? hover : click.value()));
                y += lay.height() + 2;
            }
            if (inlineOn("line", q)) {
                drawInlineField(fr, r.x() + 6, y + 2, r.width() - 12);
                y += 18;
            }
            if (!choiceFor.isEmpty()) {
                for (Reward rw : q.rewards) {
                    if (rw.id().equals(choiceFor) && rw.type().equals("choice")) {
                        RewardTable table = book.tables().get(QuestText.tableId(rw));
                        if (table != null) {
                            fr.drawStringWithShadow("Choose one:", r.x() + 7, y + 2, 0xFFAA00);
                            y += 14;
                            int idx = 0;
                            for (WeightedReward wr : table.entries) {
                                final int index = idx++;
                                final Reward owner = rw;
                                y = button(fr, r.x() + 7, y, QuestText.reward(wr.reward()), () -> {
                                    QuestNet.claim(owner.id(), index);
                                    choiceFor = "";
                                });
                            }
                        }
                    }
                }
            }
        }
        if (details && (isEditing() || pages > 1)) {
            int rowY = y + 2;
            if (isEditing()) {
                // 56 x 14, edge #1B1D1E, as measured from FTB Quests
                Rect btn = new Rect(mid - 28, rowY, 56, 14);
                Ui.box(btn, btn.contains(lastMouseX, lastMouseY) ? 0xFF3A404B : 0xFF2C3138);
                Ui.questsGui("settings", btn.x() + 5, btn.y() + 3, 8, 8, 0xFFFFFFFF);
                fr.drawStringWithShadow("Edit", btn.x() + 15, btn.y() + 3, 0xFFFFFF);
                fr.drawStringWithShadow("\u25BC", btn.x() + 39, btn.y() + 3, 0xFFFFFF);
                final Quest edited = q;
                addHit(btn, () -> openMenu(btn.x(), btn.y() + 18, editMenu(edited)), "Edit this quest");
            }
            if (pages > 1) {
                // bottom-right group: "<- 2/3 ->", each arrow only where there is a page to go to
                int last = pages - 1;
                String counter = (descPage + 1) + "/" + pages;
                int cw = fr.getStringWidth(counter);
                int rx = right - 6;
                int by = rowY + 1;
                if (descPage < last) {
                    Rect rb = new Rect(rx - 20, by, 20, 15);
                    pageArrow(fr, rb, "right", ">");
                    addHit(rb, () -> descPage++, "Next page");
                    rx -= 23;
                }
                fr.drawStringWithShadow(counter, rx - cw, by + 4, 0xAAAAAA);
                rx -= cw + 4;
                if (descPage > 0) {
                    Rect lb = new Rect(rx - 20, by, 20, 15);
                    pageArrow(fr, lb, "left", "<");
                    addHit(lb, () -> descPage--, "Previous page");
                }
            }
            y = rowY + 20;
        }
        // a quest with nothing to read (no subtitle, text or choices) has no body below its tasks and rewards
        boolean bare = details && q.subtitle.isEmpty() && !q.optional
                && (q.description.isEmpty() || !isEditing() && !p.isCompleted(q.id) && textHiddenRule(ClientQuests.book(), q)) && choiceFor.isEmpty()
                && !isEditing() && !inlineOn("line", q);
        detailContentHeight = bare ? y - top + bar + 1 : y + 4 - top + bar + 1;
        clip = null;

        // ---- dependency / dependant list popup (over everything)
        unscissor();
        if (!depPopup.isEmpty()) {
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT); // the list goes over the item pictures below it
            drawDependencyPopup(fr, book, p, q, r, y, dependants);
        }
        scissor(detailRect());
    }

    private void pageArrow(FontRenderer fr, Rect box, String icon, String fallback) {
        Ui.box(box, box.contains(lastMouseX, lastMouseY) ? 0xFF2E323B : 0xFF1F2128);
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/" + icon + ".png", box.x() + 4, box.y() + 1, 12, 12, 0xFFFFFFFF)) {
            fr.drawStringWithShadow(fallback, box.x() + 8, box.y() + 4, 0xFFFFFF);
        }
    }

    /** Edit menu: adds a page break and a placeholder page to the description. */
    private static void appendPage(Quest q) {
        addLocalLines(q, List.of(RichText.PAGE_BREAK, "(new page placeholder text)"));
    }

    /** Opens a quest and moves the camera onto it (switching chapter if it is in another one). */
    private void focusQuest(String questId) {
        QuestBook book = ClientQuests.book();
        Quest q = book == null ? null : book.quest(questId);
        Chapter c = q == null ? null : book.chapterOf(questId);
        if (q == null || c == null) {
            return;
        }
        chapterId = c.id;
        selectedQuest = q.id;
        Rect map = mapRect();
        viewFor(c, map).centerOn(q.x * GRID, q.y * GRID, map.x() + map.width() / 2.0, map.y() + map.height() / 2.0);
    }

    /** A quest's title with its own formatting; plain titles are white. On hover plain text turns yellow and colours get stronger. */
    private static void drawRichTitle(FontRenderer fr, String raw, int x, int y, int maxWidth, boolean hover) {
        List<RichText.Line> lines = RichText.parseEntry(raw);
        int cx = x;
        if (!lines.isEmpty()) {
            for (RichText.Element e : lines.get(0).elements()) {
                if (!(e instanceof RichText.Span span) || cx - x >= maxWidth) {
                    continue;
                }
                int rgb = span.rgb() == RichText.NO_COLOR ? 0xFFFFFF : span.rgb();
                if (hover) {
                    rgb = span.rgb() == RichText.NO_COLOR ? 0xFFFF6B : saturate(rgb);
                }
                if (span.rainbow()) {
                    String text = RichText.RAINBOW_MARK + DescriptionView.prefix(span) + span.text();
                    Ui.drawText(fr, text, cx, y, 0xFFFFFFFF, maxWidth - (cx - x));
                    cx += Math.min(Ui.width(fr, text), maxWidth - (cx - x));
                    continue;
                }
                String piece = fr.trimStringToWidth(DescriptionView.prefix(span) + span.text(), maxWidth - (cx - x));
                fr.drawStringWithShadow(piece, cx, y, 0xFF000000 | rgb);
                cx += fr.getStringWidth(piece);
            }
        }
    }

    private static int saturate(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        float s = hsb[1] >= 0.6f ? hsb[1] * 0.78f : Math.min(1f, hsb[1] * 1.35f + 0.1f); // very saturated: calmer; weak: stronger
        return java.awt.Color.HSBtoRGB(hsb[0], s, Math.min(1f, hsb[2] * 1.05f)) & 0xFFFFFF;
    }

    private void drawDependencyPopup(FontRenderer fr, QuestBook book, TeamProgress p, Quest q, Rect r, int sectionY, List<Quest> dependants) {
        List<Quest> list = new ArrayList<>();
        int hidden = 0;
        if (depPopup.equals("deps")) {
            for (String id : q.dependencies) {
                Quest d = book.quest(id);
                if (d != null && shown(book, p, d)) {
                    list.add(d);
                } else {
                    hidden++;
                }
            }
        } else {
            for (Quest d : dependants) {
                if (shown(book, p, d)) {
                    list.add(d);
                } else {
                    hidden++;
                }
            }
        }
        int w = 80;
        for (Quest d : list) {
            w = Math.max(w, Ui.width(fr, QuestText.title(d)) + 12);
        }
        w = Math.min(w, 230);
        int h = 6 + (list.size() + (hidden > 0 ? 1 : 0)) * 14;
        int x = depPopup.equals("deps") ? r.x() + 14 : r.x() + r.width() - w - 14;
        int y = r.y() + 44;
        popupRect = new Rect(x, y, w, h);
        Ui.box(popupRect, 0xF2202329);
        int ly = y + 4;
        for (Quest d : list) {
            Rect row = new Rect(x + 1, ly - 1, w - 2, 13);
            if (row.contains(lastMouseX, lastMouseY)) {
                Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0x40FFFFFF);
            }
            drawRichTitle(fr, QuestText.rawTitle(d), x + 5, ly + 1, w - 10, row.contains(lastMouseX, lastMouseY));
            final String id = d.id;
            hits.add(new Hit(row, () -> {
                depPopup = "";
                focusQuest(id);
            }, null));
            ly += 14;
        }
        if (hidden > 0) {
            fr.drawStringWithShadow("+ " + hidden + " hidden", x + 5, ly + 1, 0xFFFFFF);
        }
    }

    /** "< Page 2/3 >" row above a paged description; returns the y below it. */
    private int pageControls(FontRenderer fr, int x, int y, int pages) {
        String label = "Page " + (descPage + 1) + "/" + pages;
        int bw = 14;
        rect(x, y, bw, 12, descPage > 0 ? 0xFF3A3A55 : 0xFF25252F);
        fr.drawString("<", x + 4, y + 2, 0xFFFFFF);
        if (descPage > 0) {
            addHit(new Rect(x, y, bw, 12), () -> descPage--, null);
        }
        fr.drawString(label, x + bw + 6, y + 2, 0xAAAAAA);
        int nx = x + bw + 12 + fr.getStringWidth(label);
        rect(nx, y, bw, 12, descPage < pages - 1 ? 0xFF3A3A55 : 0xFF25252F);
        fr.drawString(">", nx + 4, y + 2, 0xFFFFFF);
        if (descPage < pages - 1) {
            addHit(new Rect(nx, y, bw, 12), () -> descPage++, null);
        }
        return y + 16;
    }

    /**
     * The click action of a chapter image. Open a URI (asks first, http and https only), Jump to a Quest and Show Recipes
     * for Item work; Run a Command, Post Custom Event and Show a Docs Page belong to the newer game and say so.
     *
     * @return whether the image had an action (the click is used up)
     */
    private boolean runImageAction(QuestBook book, Definitions.ChapterImage image) {
        DataNode.Compound click = image.raw().getCompound("click_action");
        String type = click.getString("type", "");
        String data = click.getString("data", "").trim();
        if (type.isEmpty()) {
            return false;
        }
        playClick();
        switch (type) {
            case "open_uri" -> onLink(book, new RichText.Click("open_url", data));
            case "open_quest" -> onLink(book, new RichText.Click("change_page", data));
            case "show_recipe" -> {
                net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(data));
                if (item == null || !JeiBridge.available() || !JeiBridge.showRecipes(new ItemStack(item))) {
                    notice("No recipes to show for " + data);
                }
            }
            default -> notice("This click action (" + type + ") is not available in 1.12.2");
        }
        return true;
    }

    /** Click on a description link: web links ask first (http/https only); quest links open that quest. */
    private void onLink(QuestBook book, RichText.Click click) {
        String value = click.value() == null ? "" : click.value().trim();
        if (click.action().equals("change_page")) {
            Quest target = book.quest(value);
            if (target != null) {
                selectedQuest = target.id;
            } else {
                notice("Unknown quest link");
            }
        } else if (click.action().equals("open_url")) {
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                notice("Link refused: only http and https links can be opened");
                return;
            }
            final net.minecraft.client.gui.GuiScreen back = mc().currentScreen;
            final String url = value;
            mc().displayGuiScreen(new net.minecraft.client.gui.GuiConfirmOpenLink((result, id) -> {
                if (result) {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    } catch (Exception ignored) {
                        // no desktop available: the player can still use Copy to Clipboard
                    }
                }
                mc().displayGuiScreen(back);
            }, url, 0, false));
        }
    }

    private static void notice(String text) {
        if (mc().player != null) {
            mc().player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§c" + text), true);
        }
    }

    private void drawPinButton(FontRenderer fr, Quest q, int x, int y) {
        boolean pinned = ClientQuests.pins().contains(q.id);
        boolean art = QuestArt.available() && QuestArt.has("textures/gui/pin.png");
        int w = art ? 16 : fr.getStringWidth(pinned ? "Unpin" : "Pin") + 8;
        Rect r = new Rect(art ? x : x - w + 16, y, w, 16);
        boolean hover = r.contains(lastMouseX, lastMouseY);
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), hover ? 0xFF4A4A68 : pinned ? 0xFF5A4A1A : 0xFF2A2A3A);
        if (art) {
            QuestArt.quad(QuestArt.guiTexture("pin"), r.x(), r.y(), 16, 16, pinned ? 0xFFFFFFFF : 0x99FFFFFF);
        } else {
            fr.drawStringWithShadow(pinned ? "Unpin" : "Pin", r.x() + 4, r.y() + 4, 0xFFFFFF);
        }
        final String id = q.id;
        hits.add(new Hit(r, () -> QuestNet.togglePin(id), pinned ? "Stop tracking" : "Track this quest on screen"));
    }


    // ------------------------------------------------------------------ editing


    private static boolean isEditing() {
        return editing && ClientQuests.canEdit();
    }

    /** In edit mode every quest is shown, including hidden ones, so they can be edited. */
    private static boolean shown(QuestBook book, TeamProgress p, Quest q) {
        return isEditing() || QuestRules.isVisible(book, p, q);
    }

    private void addHitR(Rect r, Runnable action, String tip, Runnable right) {
        if (clip == null || (r.y() + r.height() > clip.y() && r.y() < clip.y() + clip.height())) {
            hits.add(new Hit(r, action, tip, right));
        }
    }

    /** The change history and, below it, the strip with the cursor, the selection, the language and the view centre (edit mode). */
    private void drawBottomBar(FontRenderer fr, int mouseX, int mouseY) {
        if (!isEditing()) {
            return;
        }
        QuestBook book = ClientQuests.book();
        Chapter chapter = book == null ? null : currentChapter(book);
        int right = bounds.x() + bounds.width();
        int numbersRight = right - 30; // the numbers stop before the player preferences button
        int left = bounds.x() + listWidth();
        int stripH = 12;
        int stripY = bounds.y() + bounds.height() - stripH;
        ChangeLog.draw(fr, left, stripY, right - left);
        Gui.drawRect(left, stripY, right, stripY + stripH, 0x4D000000);
        if (chapter == null) {
            return;
        }
        ViewTransform v = viewFor(chapter, mapRect());
        Rect map = mapRect();
        double cx = v.worldX(map.x() + map.width() / 2.0) / GRID;
        double cy = v.worldY(map.y() + map.height() / 2.0) / GRID;
        double mx = Math.round(v.worldX(mouseX) / GRID * 2.0) / 2.0;
        double my = Math.round(v.worldY(mouseY) / GRID * 2.0) / 2.0;
        int selected = SELECTION.isEmpty() ? (chapter.quests.stream().anyMatch(q -> q.id.equals(selectedQuest)) ? 1 : 0)
                : (int) chapter.quests.stream().filter(q -> SELECTION.contains(q.id)).count();
        ClientPrefs.ensureLoaded();
        String lang = ClientPrefs.localeOverride.isEmpty()
                ? mc().gameSettings.language.toLowerCase(java.util.Locale.ROOT) + " [Auto]"
                : ClientPrefs.localeOverride + " [Manual]";
        GlStateManager.pushMatrix();
        GlStateManager.translate(left + 6, stripY + 4, 0);
        GlStateManager.scale(0.5f, 0.5f, 1f);
        int x = 0;
        String[] parts = {
            String.format(java.util.Locale.ROOT, "Cursor: [%+.2f, %+.2f]", mx, my),
            "Selected: " + selected + "/" + chapter.quests.size(),
            "Lang: " + lang,
        };
        String center = String.format(java.util.Locale.ROOT, "Center: [%.2f, %.2f]", cx, cy);
        int room = Math.round((numbersRight - 6 - (left + 6)) / 0.5f) - fr.getStringWidth(center) - 40; // what the left side may use
        for (String part : parts) {
            if (x + fr.getStringWidth(part) > room) {
                break; // never runs into the centre numbers
            }
            fr.drawStringWithShadow(part, x, 0, 0xFFFFFF);
            x += fr.getStringWidth(part) + 40;
        }
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 1f);
        fr.drawStringWithShadow(center, (numbersRight - 6) / 0.5f - fr.getStringWidth(center), (stripY + 4) / 0.5f, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    /** Whether clicking an item of this quest shows its recipes (JEI present, and not switched off for the quest or the book). */
    private static boolean jeiEnabled(QuestBook book, Quest q) {
        return JeiBridge.available() && !q.disableJei.resolve(book.settings.getBool("default_quest_disable_jei", false));
    }

    /** Undo the last edit (edits that were not sent yet go first, so the server undoes the very last one). */
    private void undoEdit() {
        if (ClientQuests.undoCount() > 0 || TableEdits.hasPending()) {
            ChangeLog.add("Undo", "", TableEdits.hasPending() ? "" : ClientQuests.undoLabel());
            ChangeLog.noteUndo();
            send("undo", DataNode.Compound.empty());
        }
    }

    private void redoEdit() {
        if (ClientQuests.redoCount() > 0 && !TableEdits.hasPending()) {
            ChangeLog.add("Redo", "", ClientQuests.redoLabel());
            ChangeLog.noteRedo();
            send("redo", DataNode.Compound.empty());
        }
    }

    // ------------------------------------------------------------------ quick edit properties

    private static final List<String> SHAPE_LABELS_Q = List.of("Default", "Circle", "Square", "Diamond", "Rounded Square", "Pentagon", "Hexagon", "Octagon", "Heart", "Gear", "<None>");
    private static final List<String> SHAPE_VALUES_Q = List.of("", "circle", "square", "diamond", "rsquare", "pentagon", "hexagon", "octagon", "heart", "gear", "none");
    private static final List<String> TRI_LABELS = List.of("Default", "True", "False");
    private static final List<String> TRI_VALUES = List.of("default", "true", "false");

    /** A switch in the quick menu: a check mark that is lit while it is on; clicking flips it. */
    private MenuItem quickSwitch(Quest q, String label, String key, boolean on) {
        return new MenuItem(label, "", on ? "lib:check" : "lib:check@dim", false,
                () -> send("set_prop", args("id", q.id, "key", key, "value", String.valueOf(!on))));
    }

    /** A setting with several values: a gear; clicking opens the values where it was clicked. */
    private MenuItem quickChoice(Quest q, String label, String key, List<String> labels, List<String> values, String current) {
        return new MenuItem(label, "", "settings", false, () -> {
            List<MenuItem> options = new ArrayList<>();
            options.add(new MenuItem(label, "", "", false, null));
            for (int i = 0; i < labels.size(); i++) {
                final String value = values.get(i);
                options.add(new MenuItem(labels.get(i), "", value.equalsIgnoreCase(current) ? "lib:check" : "", i == 0,
                        () -> send("set_prop", args("id", q.id, "key", key, "value", value))));
            }
            menu = options;
            menuX = lastMouseX;
            menuY = lastMouseY;
        });
    }

    private MenuItem quickTri(Quest q, String label, String key, Tristate now) {
        return quickChoice(q, label, key, TRI_LABELS, TRI_VALUES, now.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** "Quick Edit Properties...": the quest's main settings as a menu, in FTB's order, opened where the entry was clicked. */
    private void quickProperties(Quest q, int x, int y) {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(RichText.stripCodes(QuestText.titlePlain(q)), "", "", false, null));
        MenuItem first = quickSwitch(q, "Disable Completion Toast", "disable_toast", q.disableToast);
        items.add(new MenuItem(first.label(), "", first.icon(), true, first.action()));
        items.add(quickChoice(q, "Shape", "shape", SHAPE_LABELS_Q, SHAPE_VALUES_Q, q.shape.isEmpty() || q.shape.equals("default") ? "" : q.shape));
        List<String> presetLabels = new ArrayList<>();
        presetLabels.add("None");
        QuestBook presetBook = ClientQuests.book();
        if (presetBook != null) {
            presetLabels.addAll(presetBook.settings.getCompound("presets").entries().keySet());
        }
        items.add(quickChoice(q, "Visual Preset", "preset", presetLabels, presetLabels, q.preset.isEmpty() ? "None" : q.preset));
        items.add(quickTri(q, "Hide Quest Until Dependencies Complete", "hide_deps_complete", q.hideUntilDepsComplete));
        items.add(quickTri(q, "Hide Quest Until Dependencies Visible", "hide_deps_visible", q.hideUntilDepsVisible));
        items.add(quickSwitch(q, "Invisible Until Completed", "invisible", q.invisible));
        items.add(quickTri(q, "Hide Details Until Startable", "hide_details", q.hideDetailsUntilStartable));
        items.add(quickTri(q, "Hide Text Until Quest is Completed", "hide_text_until_complete", q.hideTextUntilComplete));
        items.add(quickSwitch(q, "Hide Lock Icon", "hide_lock_icon", q.hideLockIcon));
        items.add(quickChoice(q, "Dependency Requirement", "dependency_requirement",
                List.of("All Completed", "One Completed", "All Started", "One Started"),
                List.of("ALL_COMPLETED", "ONE_COMPLETED", "ALL_STARTED", "ONE_STARTED"), q.dependencyMode.name()));
        items.add(quickTri(q, "Hide Dependency Lines", "hide_dep_lines", q.hideDependencyLines));
        items.add(quickSwitch(q, "Hide Dependent Lines", "hide_dependent_lines", q.hideDependentLines == Tristate.TRUE));
        items.add(quickTri(q, "Disable JEI Recipe", "disable_jei", q.disableJei));
        items.add(quickTri(q, "Repeatable Quest", "repeatable", q.canRepeat));
        items.add(quickSwitch(q, "Optional Quest", "optional", q.optional));
        items.add(quickSwitch(q, "Ignore reward blocking", "ignore_reward_blocking", q.ignoreRewardBlocking));
        items.add(quickChoice(q, "Progression Mode", "progression_mode", List.of("Default", "Linear", "Flexible"),
                List.of("DEFAULT", "LINEAR", "FLEXIBLE"), q.progressionMode.name()));
        items.add(quickTri(q, "Sequential Task Completion", "sequential", q.requireSequentialTasks));
        menu = items;
        menuX = x;
        menuY = y;
    }


    // ------------------------------------------------------------------ right-click menus of tasks and rewards

    private static MenuItem menuHeader(String text) {
        return new MenuItem("\"" + text + "\"", "", "", false, null);
    }

    /** What a right click on a task offers in edit mode, in FTB's order. */
    private void taskMenu(Quest q, Task t) {
        TeamProgress p = ClientQuests.progress();
        boolean done = p != null && QuestRules.taskDone(p, t);
        int at = q.tasks.indexOf(t);
        List<MenuItem> items = new ArrayList<>();
        items.add(menuHeader(RichText.stripCodes(QuestText.taskTitle(t))));
        items.add(new MenuItem("Move Left", "", "lib:left", true, () -> {
            if (at > 0) {
                send("move_task", args("id", t.id(), "by", -1));
            }
        }));
        items.add(new MenuItem("Move Right", "", "lib:right", false, () -> {
            if (at < q.tasks.size() - 1) {
                send("move_task", args("id", t.id(), "by", 1));
            }
        }));
        if (t.type().equals("item")) {
            items.add(new MenuItem("Grab Copy of Item", "", "lib:add_gray", false, () -> grabCopy(q, t)));
        }
        items.add(new MenuItem("Use Task Icon as Quest Icon", "", "settings", false,
                () -> send("set_icon", args("kind", "quest", "id", q.id, "mode", "task", "task", t.id()))));
        items.add(new MenuItem("Edit", "", "settings", false, () -> mc().displayGuiScreen(new TaskRewardScreen(mc().currentScreen, t, null))));
        items.add(new MenuItem("Quick Edit Properties...", "", "settings", false, () -> quickTask(q, t)));
        items.add(new MenuItem("Delete", "", "lib:remove", false, () -> send("remove_task", args("id", t.id()))));
        if (done) {
            items.add(new MenuItem("Reset Progress", "", "lib:undo", false, () -> send("reset_task", args("id", t.id()))));
        } else {
            items.add(new MenuItem("Complete Instantly", "", "lib:check", false, () -> send("complete_task", args("id", t.id()))));
        }
        items.add(new MenuItem("Copy ID", "", "lib:info", false, () -> GuiScreen.setClipboardString(t.id())));
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    /** Takes a copy of the task's item; a collection or ore dictionary entry first asks which of its items. */
    private void grabCopy(Quest q, Task t) {
        DataNode spec = t.params().get("item");
        List<com.levviata.reflibquests.game.ItemResolver.Resolved> set = com.levviata.reflibquests.game.ItemSet.resolveAll(spec, ClientQuests.idMap());
        boolean collection = com.levviata.reflibquests.game.ItemSet.isFilter(spec) || set.size() > 1;
        if (!collection || set.isEmpty()) {
            send("grab_item", args("id", t.id(), "index", 0));
            return;
        }
        final List<ItemStack> shown = new ArrayList<>();
        for (com.levviata.reflibquests.game.ItemResolver.Resolved res : set) {
            shown.add(res.toStack(1));
        }
        String name = t.params().getString("title", "");
        if (name.isEmpty()) {
            name = com.levviata.reflibquests.game.ItemSet.suggestedName(spec);
        }
        final String shownName = name.isEmpty() ? QuestText.title(q) : RichText.toRainbowAware(name);
        mc().displayGuiScreen(ValidItemsScreen.forCopying(mc().currentScreen, shownName, shown, index -> {
            ItemStack chosen = shown.get(index);
            String regName = chosen.getItem().getRegistryName() == null ? "" : chosen.getItem().getRegistryName().toString();
            send("grab_item", args("id", t.id(), "index", index, "item", regName, "damage", chosen.getMetadata()));
        }));
    }

    /** What a right click on a reward offers in edit mode. */
    private void rewardMenu(Quest q, Reward rw) {
        int at = q.rewards.indexOf(rw);
        List<MenuItem> items = new ArrayList<>();
        items.add(menuHeader(RichText.stripCodes(QuestText.reward(rw))));
        items.add(new MenuItem("Move Left", "", "lib:left", true, () -> {
            if (at > 0) {
                send("move_reward", args("id", rw.id(), "by", -1));
            }
        }));
        items.add(new MenuItem("Move Right", "", "lib:right", false, () -> {
            if (at < q.rewards.size() - 1) {
                send("move_reward", args("id", rw.id(), "by", 1));
            }
        }));
        items.add(new MenuItem("Edit", "", "settings", false, () -> mc().displayGuiScreen(new TaskRewardScreen(mc().currentScreen, null, rw))));
        items.add(new MenuItem("Quick Edit Properties...", "", "settings", false, () -> quickReward(rw)));
        if (com.levviata.reflibquests.model.BookEditor.TABLE_REWARD_TYPES.contains(rw.type())) {
            final String tableToEdit = com.levviata.reflibquests.io.Ids.tableId(rw.params());
            items.add(new MenuItem("Edit Reward Table", "", "settings", false, () -> mc().displayGuiScreen(new RewardTablesScreen(mc().currentScreen, tableToEdit))));
        }
        items.add(new MenuItem("Delete", "", "lib:remove", false, () -> send("remove_reward", args("id", rw.id()))));
        items.add(new MenuItem("Copy ID", "", "lib:info", false, () -> GuiScreen.setClipboardString(rw.id())));
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    /** A setting with several values, opened as a list where it was clicked; the current one has a check mark. */
    private MenuItem quickList(String label, String sendOp, String id, String key, List<String> labels, List<String> values, String current) {
        return new MenuItem(label, "", "settings", false, () -> {
            List<MenuItem> options = new ArrayList<>();
            options.add(new MenuItem(label, "", "", false, null));
            for (int i = 0; i < labels.size(); i++) {
                final String value = values.get(i);
                options.add(new MenuItem(labels.get(i), "", value.equalsIgnoreCase(current) ? "lib:check" : "", i == 0,
                        () -> send(sendOp, args("id", id, "key", key, "value", value))));
            }
            menu = options;
            menuX = lastMouseX;
            menuY = lastMouseY;
        });
    }

    private void quickTask(Quest q, Task t) {
        List<MenuItem> items = new ArrayList<>();
        items.add(menuHeader(RichText.stripCodes(QuestText.taskTitle(t))));
        boolean toastOff = t.params().getBool("disable_toast", false);
        items.add(new MenuItem("Disable Completion Toast", "", toastOff ? "lib:check" : "lib:check@dim", true,
                () -> send("set_task_prop", args("id", t.id(), "key", "disable_toast", "value", String.valueOf(!toastOff)))));
        boolean optional = t.optionalTask();
        items.add(new MenuItem("Optional Task", "", optional ? "lib:check" : "lib:check@dim", false,
                () -> send("set_task_prop", args("id", t.id(), "key", "optional_task", "value", String.valueOf(!optional)))));
        if (t.type().equals("item")) {
            items.add(quickList("Consume Items", "set_task_prop", t.id(), "consume_items", TRI_LABELS, TRI_VALUES, triOf(t, "consume_items")));
            items.add(quickList("Only Detect From Crafting", "set_task_prop", t.id(), "only_from_crafting", TRI_LABELS, TRI_VALUES, triOf(t, "only_from_crafting")));
            String match = t.params().getString("match_components", "default");
            items.add(quickList("Match Data Components", "set_task_prop", t.id(), "match_components",
                    List.of("Default", "Fuzzy", "Exact"), List.of("default", "fuzzy", "exact"), match));
            boolean taskScreen = t.params().getBool("task_screen_only", false);
            items.add(new MenuItem("Only From Task Screen", "", taskScreen ? "lib:check" : "lib:check@dim", false,
                    () -> send("set_task_prop", args("id", t.id(), "key", "task_screen_only", "value", String.valueOf(!taskScreen)))));
        }
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    private static String triOf(Task t, String key) {
        return !t.params().has(key) ? "default" : t.params().getBool(key, false) ? "true" : "false";
    }

    private MenuItem rewardSwitch(Reward rw, String label, String key, boolean first) {
        boolean on = rw.params().getBool(key, false);
        return new MenuItem(label, "", on ? "lib:check" : "lib:check@dim", first,
                () -> send("set_reward_prop", args("id", rw.id(), "key", key, "value", String.valueOf(!on))));
    }

    private void quickReward(Reward rw) {
        List<MenuItem> items = new ArrayList<>();
        items.add(menuHeader(RichText.stripCodes(QuestText.reward(rw))));
        String team = !rw.params().has("team_reward") ? "default" : rw.params().getBool("team_reward", false) ? "true" : "false";
        MenuItem first = quickList("Per-Team Reward", "set_reward_prop", rw.id(), "team_reward", TRI_LABELS, TRI_VALUES, team);
        items.add(new MenuItem(first.label(), "", first.icon(), true, first.action()));
        List<String> labels = List.of("Default", "Disabled", "Enabled", "Enabled, No Toast", "Enabled, Invisible");
        List<String> values = List.of("default", "disabled", "enabled", "no_toast", "invisible");
        items.add(quickList("Auto-claim", "set_reward_prop", rw.id(), "auto", labels, values, rw.params().getString("auto", "default")));
        items.add(rewardSwitch(rw, "Exclude from Claim All", "exclude_from_claim_all", false));
        items.add(rewardSwitch(rw, "Ignore reward blocking", "ignore_reward_blocking", false));
        items.add(rewardSwitch(rw, "Disable Reward Screen Blur", "disable_reward_screen_blur", false));
        items.add(rewardSwitch(rw, "Only One", "only_one", false));
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }


    /** The icons of a chapter's quests, rebuilt every couple of seconds (a chapter can have a thousand quests). */
    private static final Map<String, Object[]> CHAPTER_ICON_CACHE = new HashMap<>();

    /** The only quest's icon, or the quests' icons in turn; null for an empty chapter. */
    private static ItemStack chapterIconFromQuests(Chapter c) {
        long now = System.currentTimeMillis();
        Object[] cached = CHAPTER_ICON_CACHE.get(c.id);
        if (cached == null || now - (Long) cached[0] > 2000 || (Integer) cached[1] != c.quests.size()) {
            List<ItemStack> icons = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Quest q : c.quests) {
                ItemStack s = QuestText.iconOf(q);
                if (s != null && !s.isEmpty() && seen.add(s.getItem().getRegistryName() + "/" + s.getMetadata()) && icons.size() < 40) {
                    icons.add(s);
                }
            }
            cached = new Object[] {now, c.quests.size(), icons};
            CHAPTER_ICON_CACHE.put(c.id, cached);
        }
        @SuppressWarnings("unchecked")
        List<ItemStack> icons = (List<ItemStack>) cached[2];
        return icons.isEmpty() ? null : icons.get((int) ((now / 1200) % icons.size()));
    }

    /** What a right click on a chapter offers in edit mode, in FTB's order. */
    private void chapterMenu(Chapter c) {
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        if (book == null) {
            return;
        }
        final String id = c.id;
        List<Chapter> siblings = new ArrayList<>();
        for (Chapter o : book.chapters()) {
            if (o.groupId.equals(c.groupId)) {
                siblings.add(o);
            }
        }
        int at = siblings.indexOf(c);
        boolean canUp = at > 0;
        boolean canDown = at >= 0 && at < siblings.size() - 1;
        boolean done = p != null && QuestRules.chapterCompleted(p, c);
        List<MenuItem> items = new ArrayList<>();
        items.add(menuHeader(RichText.stripCodes(c.title.isEmpty() ? c.id : c.title)));
        items.add(new MenuItem("Move", "", canUp ? "lib:up" : "lib:up@off", true, canUp ? () -> send("move_chapter", args("id", id, "by", -1)) : () -> { }));
        items.add(new MenuItem("Move", "", canDown ? "lib:down" : "lib:down@off", false, canDown ? () -> send("move_chapter", args("id", id, "by", 1)) : () -> { }));
        items.add(new MenuItem("Change Group", "", "lib:color_rgb", false, () -> chooseGroup(id)));
        items.add(new MenuItem("Edit", "", "settings", false, () -> mc().displayGuiScreen(new ChapterPropertiesScreen(mc().currentScreen, c))));
        items.add(new MenuItem("Quick Edit Properties...", "", "settings", false, () -> quickChapter(c)));
        items.add(new MenuItem("Delete", "", "lib:remove", false, () -> {
            Runnable doDelete = () -> {
                send("delete_chapter", args("id", id));
                if (id.equals(chapterId)) {
                    chapterId = "";
                    selectedQuest = "";
                }
            };
            if (c.quests.isEmpty()) {
                doDelete.run();
            } else {
                openMenu(lastMouseX, lastMouseY,
                        menuHeader(RichText.stripCodes(c.title.isEmpty() ? c.id : c.title)),
                        new MenuItem("Really delete this chapter?", "", "lib:remove", true, doDelete),
                        new MenuItem("Keep it", "", "", false, () -> { }));
            }
        }));
        items.add(new MenuItem("Reset Progress", "", "lib:undo", false, () -> send("reset_progress", args("chapter", id))));
        items.add(new MenuItem("Complete Instantly", "", "lib:check", false, () -> send("complete_quest", args("chapter", id))));
        items.add(new MenuItem("Copy ID", "", "lib:info", false, () -> GuiScreen.setClipboardString(id)));
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    private MenuItem chapterSwitch(Chapter c, String label, String key, boolean on) {
        return new MenuItem(label, "", on ? "lib:check" : "lib:check@dim", false,
                () -> send("set_chapter_prop", args("id", c.id, "key", key, "value", String.valueOf(!on))));
    }

    /** "Quick Edit Properties..." of a chapter, in FTB's order: its switches, and the settings with several values as lists. */
    private void quickChapter(Chapter c) {
        List<MenuItem> items = new ArrayList<>();
        items.add(menuHeader(RichText.stripCodes(c.title.isEmpty() ? c.id : c.title)));
        MenuItem first = chapterSwitch(c, "Disable Completion Toast", "disable_toast", c.disableToast);
        items.add(new MenuItem(first.label(), "", first.icon(), true, first.action()));
        items.add(chapterList(c, "Default Quest Shape", "shape", SHAPE_LABELS_Q, SHAPE_VALUES_Q,
                c.defaultQuestShape.equals("default") ? "" : c.defaultQuestShape));
        items.add(chapterSwitch(c, "Always Invisible", "always_invisible", c.alwaysInvisible));
        items.add(chapterSwitch(c, "Default 'Hide Dependency Lines'", "hide_dep_lines", c.defaultHideDependencyLines));
        items.add(chapterSwitch(c, "Hide Quest Details Until Startable", "hide_details", c.hideQuestDetailsUntilStartable));
        items.add(chapterSwitch(c, "Hide Quests Until Dependencies Visible", "hide_deps_visible", c.hideQuestUntilDepsVisible));
        items.add(chapterSwitch(c, "Hide Quests Until Dependencies Complete", "hide_deps_complete", c.hideQuestUntilDepsComplete));
        items.add(chapterSwitch(c, "Hide Text Until Quest is Completed", "hide_text", c.hideTextUntilComplete));
        items.add(chapterList(c, "Default 'Progression Mode'", "progression_mode", List.of("Default", "Linear", "Flexible"),
                List.of("DEFAULT", "LINEAR", "FLEXIBLE"), c.progressionMode.name()));
        items.add(chapterSwitch(c, "Default 'Repeatable Quest'", "repeatable", c.defaultRepeatable));
        items.add(chapterList(c, "Default Item Task 'Consume Items'", "consume_items", TRI_LABELS, TRI_VALUES,
                c.defaultConsumeItems.name().toLowerCase(java.util.Locale.ROOT)));
        items.add(chapterSwitch(c, "Sequential Task Completion", "sequential", c.requireSequentialTasks));
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    /** A chapter setting with several values: a gear that opens the values where it was clicked. */
    private MenuItem chapterList(Chapter c, String label, String key, List<String> labels, List<String> values, String current) {
        return new MenuItem(label, "", "settings", false, () -> {
            List<MenuItem> options = new ArrayList<>();
            options.add(menuHeader(label));
            for (int i = 0; i < labels.size(); i++) {
                final String value = values.get(i);
                options.add(new MenuItem(labels.get(i), "", value.equalsIgnoreCase(current) ? "lib:check" : "", i == 0,
                        () -> send("set_chapter_prop", args("id", c.id, "key", key, "value", value))));
            }
            menu = options;
            menuX = lastMouseX;
            menuY = lastMouseY;
        });
    }

    /** The right-click menu of a quest in edit mode: a title, then what can be done with it. */
    private void questMenu(Quest target, int x, int y) {
        TeamProgress p = ClientQuests.progress();
        boolean done = p != null && p.isCompleted(target.id);
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("\"" + QuestText.titlePlain(target) + "\"", "", "", false, null));
        items.add(new MenuItem("Edit", "", "settings", true, () -> selectedQuest = target.id));
        items.add(new MenuItem("Move", "", "lib:up", false, () -> startQuestMove(target)));
        if (target.preset.isEmpty()) { // FTB offers no Resize while a preset decides the size (measured in 26.1.2)
            items.add(new MenuItem("Resize", "", "lib:expand", false, () -> startResize(target)));
        }
        items.add(new MenuItem("Quick Edit Properties...", "", "settings", false, () -> quickProperties(target, lastMouseX, lastMouseY)));
        items.add(new MenuItem("Add Dependency", "", "lib:add", false, () -> linkFrom = target.id));
        items.add(new MenuItem("Delete", "", "lib:remove", false, () -> openMenu(x, y,
                new MenuItem("\"" + QuestText.titlePlain(target) + "\"", "", "", false, null),
                new MenuItem("Really delete this quest?", "", "lib:remove", true, () -> {
                    send("delete_quest", args("id", target.id));
                    if (target.id.equals(selectedQuest)) {
                        selectedQuest = "";
                    }
                }),
                new MenuItem("Keep it", "", "", false, () -> { }))));
        items.add(new MenuItem("Reset Progress", "", "lib:undo", false, () -> send("reset_progress", args("id", target.id))));
        if (!done) {
            items.add(new MenuItem("Complete Instantly", "", "lib:check", false, () -> send("complete_quest", args("id", target.id))));
            if (!target.dependencies.isEmpty()) {
                items.add(new MenuItem("Complete Instantly (+Dependencies)", "", "lib:check", false,
                        () -> send("complete_quest", args("id", target.id, "deps", true))));
            }
        }
        Chapter focusChapter = ClientQuests.book() == null ? null : ClientQuests.book().chapter(chapterId);
        if (focusChapter != null) {
            boolean focused = target.id.equals(focusChapter.autofocusId);
            final String fcId = focusChapter.id;
            items.add(new MenuItem(focused ? "Clear Autofocused Quest" : "Set as Autofocused Quest", "", "lib:marker", false,
                    () -> send("set_chapter_prop", args("id", fcId, "key", "autofocus", "value", focused ? "" : target.id))));
        }
        QuestBook bezBook = ClientQuests.book();
        boolean hasDeps = bezBook != null && target.dependencies.stream().anyMatch(d -> bezBook.chapterOf(d) == bezBook.chapterOf(target.id));
        if (hasDeps) {
            items.add(new MenuItem("Edit B\u00e9zier Control Points", "", "lib:star", false, () -> startBezier(target)));
        }
        if (!target.depControlPts.isEmpty()) {
            items.add(new MenuItem("Clear B\u00e9zier Control Points", "", "", false,
                    () -> send("set_bezier", DataNode.Compound.empty().with("id", target.id).with("points", DataNode.Compound.empty()))));
        }
        items.add(new MenuItem("Copy ID", "", "lib:info", false, () -> GuiScreen.setClipboardString(target.id)));
        menu = items;
        menuX = x;
        menuY = y;
    }


    private static MenuItem typeItem(String label, String icon, Runnable action) {
        return new MenuItem(label, "", icon, false, action);
    }

    /**
     * The small box FTB Quests 26.1.2 shows when an XP task or reward is created: a heading, the amount (selected, so
     * typing replaces it) and a check mark and a cross. It opens where the mouse is; nothing is created on cancel.
     */
    private static void askNumber(String title, int initial, java.util.function.IntConsumer created) {
        ScaledResolution res = new ScaledResolution(mc());
        int px = Mouse.getX() / res.getScaleFactor();
        int py = (mc().displayHeight - Mouse.getY()) / res.getScaleFactor();
        mc().displayGuiScreen(new NamePrompt(mc().currentScreen, px + 7, py + 12, title, String.valueOf(initial), 9, text -> {
            try {
                int n = Integer.parseInt(text.trim());
                if (n > 0) {
                    created.accept(n);
                }
            } catch (NumberFormatException ignored) {
                // not a number: nothing is created
            }
        }));
    }

    /** FTB Quests opens an item picker when an item task or reward is made; the book screen is shown again once an item is chosen. */
    private static void pickItem(java.util.function.Consumer<ItemStack> chosen) {
        mc().displayGuiScreen(new ItemPickerScreen(mc().currentScreen, chosen));
    }

    /** What the server needs to know about a picked item: its registry name and variant. */
    private static DataNode.Compound itemSpec(ItemStack stack) {
        DataNode.Compound spec = DataNode.Compound.empty().with("id", String.valueOf(stack.getItem().getRegistryName()));
        if (stack.getMetadata() != 0) {
            spec = spec.with("damage", stack.getMetadata());
        }
        return spec;
    }

    private static MenuItem typeOff(String label, String icon) {
        return new MenuItem(label, "", icon + "@off", false, () -> { });
    }

    /** FTB's "Select Task Type" list, in its order. Types without support yet are listed greyed out. */
    private static MenuItem[] taskTypeMenu(String owner) {
        return new MenuItem[] {
            typeItem("Item", "item:diamond", () -> mc().displayGuiScreen(new ItemPickerScreen(mc().currentScreen, true,
                    (stack, n) -> send("add_task", args("id", owner, "type", "item", "item", itemSpec(stack), "count", n))))),
            typeItem("Custom", "lib:color_hsb", () -> send("add_task", args("id", owner, "type", "custom"))),
            typeItem("XP Levels", "item:experience_bottle", () -> askNumber("XP Levels", 1,
                    n -> send("add_task", args("id", owner, "type", "xp", "params", args("value", n, "points", false))))),
            typeItem("Visit Dimension", "item:ender_eye", () -> send("add_task", args("id", owner, "type", "dimension", "params", args("dimension", "minecraft:the_nether")))),
            typeItem("Stat", "item:iron_sword", () -> send("add_task", args("id", owner, "type", "stat", "params", args("stat", "minecraft:mob_kills", "value", 1)))), // FTB's defaults: Mob Kills, 1
            typeItem("Kill Entity", "item:diamond_sword", () -> send("add_task", args("id", owner, "type", "kill", "params", args("entity", "minecraft:zombie", "value", 100)))),
            typeItem("Location", "item:stone_pressure_plate", () -> send("add_task", args("id", owner, "type", "location"))),
            typeItem("Checkmark", "check_gray", () -> send("add_task", args("id", owner, "type", "checkmark"))),
            typeItem("Advancement", "item:wheat", () -> send("add_task", args("id", owner, "type", "advancement", "params", args("advancement", "minecraft:story/root")))),
            typeItem("Observation", "item:painting", () -> send("add_task", args("id", owner, "type", "observation", "params", args("observation_type", "block", "to_observe", "minecraft:stone", "timer", 0)))),
            typeItem("Visit Biome", "item:sapling", () -> send("add_task", args("id", owner, "type", "biome", "params", args("biome", "minecraft:plains")))),
            typeItem("Find Structure", "item:paper", () -> send("add_task", args("id", owner, "type", "structure", "params", args("structure", "minecraft:village")))),
            typeItem("Stage", "lib:controller", () -> send("add_task", args("id", owner, "type", "gamestage"))),
            typeItem("Fluid", "item:bucket", () -> send("add_task", args("id", owner, "type", "fluid", "params", args("fluid", DataNode.Compound.empty().with("fluid", "minecraft:water").with("amount", 1000))))), // FTB's default: 1000 mB of water
            typeItem("Interaction", "item:diamond_pickaxe", () -> send("add_task", args("id", owner, "type", "interaction"))),
            typeItem("Forge Energy", "item:redstone_block", () -> askNumber("Forge Energy", 1,
                    n -> send("add_task", args("id", owner, "type", "forge_energy", "params", args("value", n))))),
        };
    }

    /** FTB's "Select Reward Type" list, in its order. Table rewards ask which table to use. */
    private MenuItem[] rewardTypeMenu(String owner) {
        return new MenuItem[] {
            typeItem("Item", "item:diamond", () -> pickItem(stack -> send("add_reward", args("id", owner, "type", "item", "item", itemSpec(stack))))),
            typeItem("Choice Reward", "lib:color_rgb", () -> chooseTable(owner, "choice")),
            typeItem("All Table Reward", "lib:color_hsb", () -> chooseTable(owner, "all_table")),
            typeItem("Random Reward", "lib:dice", () -> chooseTable(owner, "random")),
            typeItem("Loot Reward", "lib:money_bag", () -> chooseTable(owner, "loot")),
            typeItem("Command", "item:command_block", () -> send("add_reward", args("id", owner, "type", "command", "params", args("command", "/say Hi, @p!")))),
            typeItem("Custom", "lib:color_hsb", () -> send("add_reward", args("id", owner, "type", "custom"))),
            typeItem("XP", "item:experience_bottle", () -> askNumber("XP", 100,
                    n -> send("add_reward", args("id", owner, "type", "xp", "params", args("xp", n))))),
            typeItem("XP Levels", "item:experience_bottle", () -> askNumber("XP Levels", 5,
                    n -> send("add_reward", args("id", owner, "type", "xp_levels", "params", args("xp_levels", n))))),
            typeItem("Advancement", "item:wheat", () -> send("add_reward", args("id", owner, "type", "advancement", "params", args("advancement", "minecraft:story/root")))),
            typeItem("Toast", "item:sign", () -> send("add_reward", args("id", owner, "type", "toast", "params", args("description", "Well done!")))),
            typeItem("Stage", "lib:controller", () -> askText("Stage", "",
                    s -> send("add_reward", args("id", owner, "type", "gamestage", "params", args("stage", s))))),
        };
    }

    /** The same small box as askNumber, for a name; nothing is created when it is empty or cancelled. */
    private static void askText(String title, String initial, java.util.function.Consumer<String> created) {
        ScaledResolution res = new ScaledResolution(mc());
        int px = Mouse.getX() / res.getScaleFactor();
        int py = (mc().displayHeight - Mouse.getY()) / res.getScaleFactor();
        mc().displayGuiScreen(new NamePrompt(mc().currentScreen, px + 7, py + 12, title, initial, 100, text -> {
            if (!text.isBlank()) {
                created.accept(text.strip());
            }
        }));
    }

    /** The "Select Reward Table" panel (with a search box) for a Choice, All Table, Random or Loot reward. */
    private void chooseTable(String owner, String type) {
        mc().displayGuiScreen(new SelectRewardTableScreen(mc().currentScreen,
                tid -> send("add_reward", args("id", owner, "type", type, "params", args("table_id", tid)))));
    }

    private void openMenu(int x, int y, MenuItem... items) {
        menu = List.of(items);
        menuX = x;
        menuY = y;
    }

    private Rect menuRect(List<MenuItem> items) {
        FontRenderer fr = mc().fontRenderer;
        int w = 40;
        int h = 4;
        for (MenuItem m : items) {
            int icon = m.icon().isEmpty() && m.action() != null ? 0 : 16;
            int shortcut = m.shortcut().isEmpty() ? 0 : fr.getStringWidth(m.shortcut()) + 10;
            w = Math.max(w, icon + fr.getStringWidth(m.label()) + shortcut + 16);
            h += MENU_ROW + (m.separator() ? 6 : 0);
        }
        int x = Math.min(menuX, bounds.x() + bounds.width() - w - 2);
        int y = Math.min(menuY, bounds.y() + bounds.height() - h - 2);
        return new Rect(x, y, w, h);
    }

    private static final int MENU_ROW = 13;

    private void drawMenu(FontRenderer fr, int mouseX, int mouseY) {
        if (menu.isEmpty()) {
            return;
        }
        Rect box = menuRect(menu);
        Ui.shadow(box);
        Ui.box(box, 0xFF2F343B);
        int y = box.y() + 2;
        for (MenuItem m : menu) {
            if (m.separator()) {
                Gui.drawRect(box.x() + 3, y + 2, box.x() + box.width() - 3, y + 4, 0xFF7C818C);
                y += 6;
            }
            Rect row = new Rect(box.x() + 1, y, box.width() - 2, MENU_ROW);
            boolean off = m.icon().endsWith("@off"); // listed for completeness, not available yet
            boolean hover = m.action() != null && !off && row.contains(mouseX, mouseY); // a header (no action) is only a title
            int tx = box.x() + 6;
            if (m.action() == null) {
                tx += 16;
            } else if (!m.icon().isEmpty()) {
                String iconName = m.icon();
                int iconColor = 0xFFFFFFFF;
                if (iconName.endsWith("@dim")) {
                    iconName = iconName.substring(0, iconName.length() - 4);
                    iconColor = 0x66FFFFFF; // switched off
                } else if (off) {
                    iconName = iconName.substring(0, iconName.length() - 4);
                    iconColor = 0x66FFFFFF;
                }
                if (iconName.startsWith("item:")) {
                    net.minecraft.item.Item menuItem = net.minecraft.item.Item.getByNameOrId(iconName.substring(5));
                    if (menuItem != null) {
                        drawItem(new ItemStack(menuItem), tx, y + 1, 0.75f);
                    }
                } else if (iconName.startsWith("gui:")) {
                    Ui.icon(Ui.LIBRARY, "textures/gui/" + iconName.substring(4) + ".png", tx, y + 2, 12, 12, iconColor);
                } else if (iconName.startsWith("lib:")) {
                    Ui.icon(Ui.LIBRARY, "textures/icons/" + iconName.substring(4) + ".png", tx, y + 2, 12, 12, iconColor);
                } else {
                    Ui.questsGui(iconName, tx, y + 2, 12, 12, iconColor);
                }
                tx += 16;
            }
            fr.drawStringWithShadow(m.label(), tx, y + 4, off ? 0x8A8D96 : hover ? 0xFFFF55 : 0xFFFFFF);
            if (!m.shortcut().isEmpty()) {
                fr.drawString(m.shortcut(), box.x() + box.width() - fr.getStringWidth(m.shortcut()) - 5, y + 4, 0x6F7384);
            }
            y += MENU_ROW;
        }
    }

    private void addHit(Rect r, Runnable action, String tip) {
        if (clip == null || (r.y() + r.height() > clip.y() && r.y() < clip.y() + clip.height())) {
            hits.add(new Hit(r, action, tip));
        }
    }

    private static DataNode.Compound args(Object... pairs) {
        DataNode.Compound c = DataNode.Compound.empty();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object v = pairs[i + 1];
            if (v instanceof String str) {
                c = c.with(key, str);
            } else if (v instanceof Integer n) {
                c = c.with(key, n);
            } else if (v instanceof Long n) {
                c = c.with(key, n);
            } else if (v instanceof Double d) {
                c = c.with(key, d);
            } else if (v instanceof Boolean b) {
                c = c.with(key, b);
            } else if (v instanceof DataNode n) {
                c = c.with(key, n);
            }
        }
        return c;
    }

    private static void send(String op, DataNode.Compound args) {
        TableEdits.send(op, args);
    }

    private void updateGhost() {
        QuestBook book = ClientQuests.book();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter == null || movingQuest == null) {
            return;
        }
        double factor = new ScaledResolution(mc()).getScaleFactor();
        double px = Mouse.getX() / factor;
        double py = (mc().displayHeight - Mouse.getY()) / factor;
        ViewTransform v = viewFor(chapter, mapRect());
        ghostX = Math.round(v.worldX(px) / GRID * 2.0) / 2.0;
        ghostY = Math.round(v.worldY(py) / GRID * 2.0) / 2.0;
    }

    private void createQuestAt(int mouseX, int mouseY) {
        QuestBook book = ClientQuests.book();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter == null) {
            return;
        }
        ViewTransform v = viewFor(chapter, mapRect());
        send("create_quest", args("chapter", chapter.id, "x", v.worldX(mouseX) / GRID, "y", v.worldY(mouseY) / GRID));
    }

    private int inlineButton(FontRenderer fr, int x, int y, String label, boolean on, Runnable action) {
        int w = fr.getStringWidth(label) + 8;
        Rect r = new Rect(x, y, w, 12);
        boolean hover = r.contains(lastMouseX, lastMouseY);
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), hover ? 0xFF6B6B8F : on ? 0xFF3C7A46 : 0xFF4A4A68);
        fr.drawStringWithShadow(label, x + 4, y + 2, 0xFFFFFF);
        addHit(r, action, null);
        return x + w + 3;
    }

    private void drawEditDetail(FontRenderer fr, QuestBook book) {
        Quest q = book.quest(selectedQuest);
        if (!selectedQuest.equals(lastPropsQuest)) {
            lastPropsQuest = selectedQuest;
            propsScroll = 0;
            confirmDelete = false;
        }
        Rect r = propsRect();
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xF2101018);
        Gui.drawRect(r.x(), r.y(), r.x() + 1, r.y() + r.height(), 0xFFB07020);
        clip = r;
        int x = r.x() + 8;
        int w = r.width() - 16;
        int top = r.y() + 8 - propsScroll;
        int y = top;
        Ui.drawText(fr, QuestText.title(q), x, y, 0xFFD060, w);
        y += 12;
        if (!q.subtitle.isEmpty()) {
            Ui.drawText(fr, RichText.toRainbowAware(q.subtitle), x, y, 0xAAAAAA, w);
            y += 12;
        }
        final Quest quest = q;
        y = button(fr, x, y, "Edit text", () -> mc().displayGuiScreen(TextEditScreen.forQuest(quest)));

        int bx = x;
        bx = inlineButton(fr, bx, y, "Optional", q.optional, () -> send("set_flag", args("id", quest.id, "flag", "optional", "value", !quest.optional)));
        bx = inlineButton(fr, bx, y, "Hidden", q.invisible, () -> send("set_flag", args("id", quest.id, "flag", "invisible", "value", !quest.invisible)));
        bx = inlineButton(fr, bx, y, "Repeat", q.canRepeat == Tristate.TRUE, () -> send("set_flag", args("id", quest.id, "flag", "repeatable", "value", quest.canRepeat != Tristate.TRUE)));
        y += 15;
        bx = x;
        inlineButton(fr, bx, y, "Ordered tasks", q.requireSequentialTasks == Tristate.TRUE, () -> send("set_flag", args("id", quest.id, "flag", "sequential", "value", quest.requireSequentialTasks != Tristate.TRUE)));
        y += 15;
        y = button(fr, x, y, "Shape: " + (q.shape.isEmpty() ? "default" : q.shape), () -> {
            int at = 0;
            for (int i = 0; i < SHAPE_CYCLE.length; i++) {
                if (SHAPE_CYCLE[i].equals(quest.shape)) {
                    at = i;
                }
            }
            send("set_shape", args("id", quest.id, "shape", SHAPE_CYCLE[(at + 1) % SHAPE_CYCLE.length]));
        });

        fr.drawStringWithShadow("Needs (finish first)", x, y, 0xFFFFFF);
        y += 11;
        for (String depId : q.dependencies) {
            Quest dep = book.quest(depId);
            String name = dep == null ? depId : QuestText.title(dep);
            Ui.drawText(fr, name, x + 4, y + 2, 0xDDDDDD, w - 26, false);
            inlineButton(fr, x + w - 22, y, "x", false, () -> send("toggle_dep", args("id", quest.id, "dep", depId)));
            y += 14;
        }
        y = button(fr, x, y, linkFrom.equals(q.id) ? "Pick the quest on the map..." : "+ Add or remove dependency", () -> linkFrom = linkFrom.equals(quest.id) ? "" : quest.id);

        fr.drawStringWithShadow("Tasks", x, y, 0xFFFFFF);
        y += 11;
        for (Task t : q.tasks) {
            for (String wrapped : fr.listFormattedStringToWidth(QuestText.task(t), w - 8)) {
                fr.drawString(wrapped, x + 4, y, 0xDDDDDD);
                y += 10;
            }
            int tx = x + 4;
            if (t.type().equals("item") || t.type().equals("xp") || t.type().equals("kill")) {
                final Task task = t;
                tx = inlineButton(fr, tx, y, "-", false, () -> send("task_value", args("id", task.id(), "value", Math.max(1, task.maxProgress() - (GuiScreen.isShiftKeyDown() ? 10 : 1)))));
                tx = inlineButton(fr, tx, y, "+", false, () -> send("task_value", args("id", task.id(), "value", task.maxProgress() + (GuiScreen.isShiftKeyDown() ? 10 : 1))));
                fr.drawString(String.valueOf(t.maxProgress()), tx + 2, y + 2, 0xAAAAAA);
                tx += 40;
            }
            final String taskId = t.id();
            inlineButton(fr, x + w - 22, y, "x", false, () -> send("remove_task", args("id", taskId)));
            y += 15;
        }
        int ax = x;
        ax = inlineButton(fr, ax, y, "+ Check", false, () -> send("add_task", args("id", quest.id, "type", "checkmark")));
        ax = inlineButton(fr, ax, y, "+ Held item", false, () -> send("add_task", args("id", quest.id, "type", "item")));
        inlineButton(fr, ax, y, "+ XP level", false, () -> send("add_task", args("id", quest.id, "type", "xp", "params", args("value", 5, "points", false))));
        y += 18;

        fr.drawStringWithShadow("Rewards", x, y, 0xFFFFFF);
        y += 11;
        for (var rw : q.rewards) {
            for (String wrapped : fr.listFormattedStringToWidth(QuestText.reward(rw), w - 28)) {
                fr.drawString(wrapped, x + 4, y + 2, 0xDDDDDD);
                y += 10;
            }
            final String rewardId = rw.id();
            inlineButton(fr, x + w - 22, y - 12, "x", false, () -> send("remove_reward", args("id", rewardId)));
            y += 4;
        }
        ax = x;
        ax = inlineButton(fr, ax, y, "+ Held item", false, () -> send("add_reward", args("id", quest.id, "type", "item")));
        ax = inlineButton(fr, ax, y, "+ 10 XP", false, () -> send("add_reward", args("id", quest.id, "type", "xp", "params", args("xp", 10))));
        inlineButton(fr, ax, y, "+ XP level", false, () -> send("add_reward", args("id", quest.id, "type", "xp_levels", "params", args("xp_levels", 1))));
        y += 22;

        y = button(fr, x, y, confirmDelete ? "Really delete this quest?" : "Delete quest", () -> {
            if (confirmDelete) {
                send("delete_quest", args("id", quest.id));
                confirmDelete = false;
                selectedQuest = "";
            } else {
                confirmDelete = true;
            }
        });
        fr.drawString("Hold an item, then use '+ Held item'", x, y + 2, 0x777788);
        y += 14;
        propsContentHeight = y - top;
        clip = null;
    }

    private static boolean consumes(QuestBook book, Task t) {
        if (t.params().has("consume_items")) {
            return t.params().getBool("consume_items", false);
        }
        boolean fallback = book.settings.getBool("default_consume_items", false);
        Quest owner = book.questOfTask(t.id());
        Chapter in = owner == null ? null : book.chapterOf(owner.id);
        return in == null ? fallback : in.defaultConsumeItems.resolve(fallback);
    }

    /** Whether the quest's text stays hidden until it is completed: its own setting, then the chapter's. */
    private static boolean textHiddenRule(QuestBook book, Quest q) {
        Chapter in = book.chapterOf(q.id);
        return q.hideTextUntilComplete.resolve(in != null && in.hideTextUntilComplete);
    }

    private int button(FontRenderer fr, int x, int y, String label, Runnable action) {
        int w = fr.getStringWidth(label) + 10;
        Rect r = new Rect(x, y, w, 12);
        boolean hover = r.contains(lastMouseX, lastMouseY);
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), hover ? 0xFF6B6B8F : 0xFF4A4A68);
        fr.drawStringWithShadow(label, x + 5, y + 2, 0xFFFFFF);
        addHit(r, action, null);
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

    /** The quests from the bottom of the stack to the top: by Z, and at the same Z the smaller ones first. */
    private List<Quest> drawOrder(Chapter chapter) {
        List<Quest> order = new ArrayList<>(chapter.quests);
        order.addAll(linkNodes);
        order.sort(java.util.Comparator.<Quest>comparingDouble(q -> q.z).thenComparingDouble(q -> com.levviata.reflibquests.model.Presets.size(ClientQuests.book(), q)));
        return order;
    }

    /** The quest links of the chapter on show, as copies of their quests standing where the links are. Rebuilt every frame. */
    private final List<Quest> linkNodes = new ArrayList<>();
    private final java.util.Map<Quest, String> linkIds = new java.util.IdentityHashMap<>();

    private void refreshLinkNodes(QuestBook book, Chapter chapter) {
        linkNodes.clear();
        linkIds.clear();
        int n = 0;
        for (Definitions.QuestLink l : chapter.links) {
            Quest orig = book.quest(l.linkedQuest());
            if (orig == null || n++ >= 200) {
                continue;
            }
            Quest copy = com.levviata.reflibquests.model.DeepCopy.quest(orig, orig.id);
            copy.x = l.x();
            copy.y = l.y();
            double own = l.raw().getDouble("size", 0);
            copy.size = own > 0 ? own : orig.size;
            copy.dependencies.clear(); // no lines run to a link
            copy.depControlPts = new java.util.LinkedHashMap<>();
            linkNodes.add(copy);
            linkIds.put(copy, l.id());
        }
    }

    /** What a right click on a quest link offers in edit mode. */
    private void linkMenu(Quest link) {
        final String linkId = linkIds.get(link);
        List<MenuItem> items = new ArrayList<>();
        items.add(menuHeader(RichText.stripCodes(QuestText.titlePlain(link))));
        items.add(new MenuItem("Edit Linked Quest", "", "settings", true, () -> focusQuest(link.id)));
        items.add(new MenuItem("Delete", "", "lib:remove", false, () -> send("delete_link", args("id", linkId))));
        items.add(new MenuItem("Copy ID", "", "lib:info", false, () -> GuiScreen.setClipboardString(linkId)));
        menu = items;
        menuX = lastMouseX;
        menuY = lastMouseY;
    }

    private Quest questAt(int mx, int my) {
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter == null || p == null) {
            return null;
        }
        Rect map = mapRect();
        if (!map.contains(mx, my) || listRect().contains(mx, my) || overPanel(mx, my)) {
            return null;
        }
        ViewTransform v = viewFor(chapter, map);
        List<Quest> order = drawOrder(chapter);
        for (int i = order.size() - 1; i >= 0; i--) {
            Quest q = order.get(i);
            double size = Math.max(4, NODE * com.levviata.reflibquests.model.Presets.size(book, q) * v.zoom());
            double cx = v.screenX(q.x * GRID);
            double cy = v.screenY(q.y * GRID);
            if (shapeContains(QuestArt.shapeName(book, chapter, q), mx - cx, my - cy, size) && shown(book, p, q)) {
                return q;
            }
        }
        return null;
    }

    /** Whether a point (relative to the node's centre) is inside the node's shape, leaning outwards by 1 px (small) or 4 px (big). */
    private static boolean shapeContains(String shape, double dx, double dy, double size) {
        double lean = size >= 30 ? 4 : 1;
        double half = size / 2 + lean;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        switch (shape) {
            case "diamond":
                return ax + ay <= half * 1.0 + lean;
            case "hexagon": {
                return ay <= half && ax <= half * 0.866 && ay <= half - ax * 0.577 + lean;
            }
            case "square":
            case "rsquare":
            case "none":
                return ax <= half && ay <= half;
            default: // circle, gear, heart, octagon, pentagon: a circle is close enough
                return ax * ax + ay * ay <= half * half;
        }
    }

    @Override
    protected boolean onClick(int mouseX, int mouseY, int button) {
        if (!imgMode.isEmpty()) {
            QuestBook ib = ClientQuests.book();
            Chapter ic = ib == null ? null : currentChapter(ib);
            Definitions.ChapterImage target = ic == null ? null : imageById(ic, imgModeId);
            if (button == 0 && target != null) {
                double[] h = imageHandle(target, viewFor(ic, mapRect()));
                if (Math.abs(mouseX - h[0]) <= 9 && Math.abs(mouseY - h[1]) <= 9) {
                    imgGrab = true;
                }
            }
            return true; // nothing else is clicked meanwhile
        }
        if (!resizeQuest.isEmpty()) {
            QuestBook rb = ClientQuests.book();
            Chapter rc = rb == null ? null : currentChapter(rb);
            if (button == 0 && rc != null && overResizeDot(viewFor(rc, mapRect()), mouseX, mouseY)) {
                resizeGrab = true;
            }
            return true; // nothing else is clicked while a quest is being resized
        }
        if (!bezierQuest.isEmpty()) {
            QuestBook bk = ClientQuests.book();
            Chapter ch = bk == null ? null : currentChapter(bk);
            if (button == 0 && ch != null) {
                Object[] h = handleAt(viewFor(ch, mapRect()), mouseX, mouseY);
                if (h != null) {
                    bezierGrabDep = (String) h[0];
                    bezierGrabIndex = (Integer) h[1];
                }
            }
            return true; // nothing else is clicked while a curve is being bent
        }
        if (!inlineKind.isEmpty()) {
            inlineKind = ""; // clicking elsewhere leaves the in-place field without saving
            return true;
        }
        if (movingQuest != null) {
            // left or middle click drops the quest at the highlighted place; right click cancels and leaves nothing selected
            endQuestMove(button == 0 || button == 2);
            if (button == 1) {
                selectedQuest = "";
            }
            return true;
        }
        if (!movingImage.isEmpty()) {
            if (button == 0) {
                dropImage();
            } else {
                cancelMove();
            }
            return true;
        }
        if (listBar != null && !detailOpen() && !searching && listBar.contains(mouseX, mouseY) && listWidth() > 0) {
            draggingListBar = true;
            scrollListTo(mouseY);
            return true;
        }
        if (!menu.isEmpty()) {
            List<MenuItem> open = menu;
            menu = List.of();
            Rect box = menuRect(open);
            if (box.contains(mouseX, mouseY) && button == 0) {
                int y = box.y() + 2;
                for (MenuItem m : open) {
                    y += m.separator() ? 6 : 0;
                    if (mouseY >= y && mouseY < y + MENU_ROW) {
                        if (m.action() != null) {
                            if (!m.icon().endsWith("@off")) {
                                playClick();
                            }
                            m.action().run();
                        }
                        break;
                    }
                    y += MENU_ROW;
                }
            }
            return true;
        }
        if (detailOpen() && !searching && !insideFocusWindow(mouseX, mouseY)) {
            // the open quest has the focus: a click anywhere else only closes it
            selectedQuest = "";
            depPopup = "";
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.rect().contains(mouseX, mouseY)) {
                if (button == 0) {
                    playClick();
                    h.action().run();
                } else if (button == 1 && h.right() != null) {
                    h.right().run();
                }
                return true;
            }
        }
        if (searching) {
            return true; // the search window is modal: clicks outside its rows do nothing
        }
        if (button == 0 && !isEditing() && mapRect().contains(mouseX, mouseY) && !listRect().contains(mouseX, mouseY)
                && !overPanel(mouseX, mouseY) && questAt(mouseX, mouseY) == null) {
            // a click on a chapter image runs its click action (FTB Quests 26.1.2 "Click Action")
            Chapter onChapter = currentChapter(ClientQuests.book());
            Definitions.ChapterImage clicked = onChapter == null ? null : imageAt(onChapter, viewFor(onChapter, mapRect()), mouseX, mouseY);
            if (clicked != null && runImageAction(ClientQuests.book(), clicked)) {
                return true;
            }
        }
        if (isEditing() && mapRect().contains(mouseX, mouseY) && !listRect().contains(mouseX, mouseY)
                && !overPanel(mouseX, mouseY)) {
            Quest hit = questAt(mouseX, mouseY);
            if (button == 1) {
                Chapter here = currentChapter(ClientQuests.book());
                Definitions.ChapterImage image = hit == null && here != null ? imageAt(here, viewFor(here, mapRect()), mouseX, mouseY) : null;
                if (image != null) {
                    imageMenu(image);
                } else if (hit == null) {
                    final int cx = mouseX;
                    final int cy = mouseY;
                    openMenu(mouseX, mouseY, new MenuItem("New Quest", () -> createQuestAt(cx, cy)));
                } else if (linkIds.containsKey(hit)) {
                    linkMenu(hit);
                } else {
                    questMenu(hit, mouseX, mouseY);
                }
                return true;
            }
            if (button == 0 && hit != null && !linkFrom.isEmpty()) {
                if (!hit.id.equals(linkFrom)) {
                    send("toggle_dep", args("id", linkFrom, "dep", hit.id));
                }
                linkFrom = "";
                return true;
            }
            // like upstream: a middle click on a quest starts moving it; the left button opens it or pans the map
            if (button == 2 && hit != null) {
                if (!linkIds.containsKey(hit)) {
                    startQuestMove(hit);
                }
                return true;
            }
            if (button == 2) {
                boxing = true; // middle drag on free space picks the quests inside the box
                boxButton = 2;
                boxX0 = mouseX;
                boxY0 = mouseY;
                return true;
            }
            boolean alt = org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_LMENU) || org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_RMENU);
            if (button == 0 && alt && hit == null) {
                boxing = true; // Alt + left drag that starts on free space does the same
                boxButton = 0;
                boxX0 = mouseX;
                boxY0 = mouseY;
                return true;
            }
            if (button == 0 && Keyboard.isKeyDown(Keyboard.KEY_RMENU) && hit != null) {
                // Right Alt + click: the quest goes on the clipboard, Ctrl + V puts a copy where the mouse is
                COPIED.clear();
                COPIED.add(hit.id);
                mc().getToastGui().add(new QuestToast("Copied object:", QuestText.titlePlain(hit), null, true));
                return true;
            }
            if (button == 0 && Keyboard.isKeyDown(Keyboard.KEY_LMENU) && hit != null) {
                mc().displayGuiScreen(new QuestPropertiesScreen(mc().currentScreen, hit)); // Left Alt + click: properties editor
                return true;
            }
        }
        if (mapRect().contains(mouseX, mouseY) && !listRect().contains(mouseX, mouseY)
                && !overPanel(mouseX, mouseY)) {
            if (button != 0) {
                return true; // only the left button pans the map or opens a quest
            }
            pressedOnMap = true;
            moved = false;
            movedDistance = 0;
            lastRawX = Mouse.getX();
            lastRawY = Mouse.getY();
            return true;
        }
        return overPanel(mouseX, mouseY) || listRect().contains(mouseX, mouseY);
    }

    @Override
    protected boolean onDrag(int mouseX, int mouseY, int button, int dx, int dy) {
        if (draggingListBar) {
            scrollListTo(mouseY);
            return true;
        }
        return pressedOnMap; // panning is done per frame in applyDragFromMouse(), not per tick here
    }

    @Override
    protected boolean onRelease(int mouseX, int mouseY, int button) {
        if (draggingListBar) {
            draggingListBar = false;
            return true;
        }
        if (boxing && button == boxButton) {
            boxing = false;
            QuestBook book = ClientQuests.book();
            Chapter chapter = book == null ? null : currentChapter(book);
            if (chapter != null) {
                ViewTransform v = viewFor(chapter, mapRect());
                int x0 = Math.min(boxX0, mouseX);
                int x1 = Math.max(boxX0, mouseX);
                int y0 = Math.min(boxY0, mouseY);
                int y1 = Math.max(boxY0, mouseY);
                boolean toggle = GuiScreen.isCtrlKeyDown();
                if (!toggle) {
                    SELECTION.clear();
                }
                for (Quest q : chapter.quests) {
                    double qx = v.screenX(q.x * GRID);
                    double qy = v.screenY(q.y * GRID);
                    if (qx >= x0 && qx <= x1 && qy >= y0 && qy <= y1) {
                        if (toggle && !SELECTION.add(q.id)) {
                            SELECTION.remove(q.id);
                        } else if (!toggle) {
                            SELECTION.add(q.id);
                        }
                    }
                }
            }
            return true;
        }
        if (!imgMode.isEmpty()) {
            if (imgGrab && button == 0) {
                endImageMode(true); // letting go of the handle sets it
            }
            return true;
        }
        if (!resizeQuest.isEmpty()) {
            if (resizeGrab && button == 0) {
                endResize(true); // letting go of the dot sets the size
            }
            return true;
        }
        if (!bezierQuest.isEmpty()) {
            bezierGrabDep = "";
            return true;
        }
        if (!pressedOnMap || button != 0) {
            return false; // only a left press opens a quest; a middle or right release never does
        }
        pressedOnMap = false;
        if (!moved) {
            Quest q = questAt(mouseX, mouseY);
            if (q != null) {
                playClick();
            }
            if (isEditing() && GuiScreen.isCtrlKeyDown() && q != null) {
                if (!SELECTION.remove(q.id)) {
                    SELECTION.add(q.id); // Ctrl + click picks or drops a quest without opening it
                }
                return true;
            }
            selectedQuest = q == null ? "" : q.id;
            if (q != null) {
                Chapter owner = ClientQuests.book() == null ? null : ClientQuests.book().chapterOf(q.id);
                if (owner != null && !owner.id.equals(chapterId)) {
                    focusQuest(q.id); // a quest link opens its quest in the chapter that quest is in
                }
            }
        }
        return true;
    }

    @Override
    protected boolean onScroll(int mouseX, int mouseY, int delta) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return false;
        }
        if (searching) {
            searchScroll -= Integer.signum(delta) * 3;
            return true;
        }
        if (propsOpen() && propsRect().contains(mouseX, mouseY)) {
            int max = Math.max(0, propsContentHeight - propsRect().height() + 16);
            propsScroll = Math.max(0, Math.min(max, propsScroll - Integer.signum(delta) * 24));
            return true;
        }
        if (detailOpen() && detailRect().contains(mouseX, mouseY)) {
            int max = Math.max(0, detailContentHeight - detailRect().height() + 16);
            detailScroll = Math.max(0, Math.min(max, detailScroll - Integer.signum(delta) * 24));
            return true;
        }
        if (detailOpen()) {
            return true; // the open quest has the focus: nothing below it scrolls or zooms
        }
        if (listRect().contains(mouseX, mouseY)) {
            int max = Math.max(0, listContentHeight - listRect().height() + 30);
            listScroll = Math.max(0, Math.min(max, listScroll - Integer.signum(delta) * 18));
            return true;
        }
        Chapter chapter = currentChapter(book);
        if (chapter != null && mapRect().contains(mouseX, mouseY)) {
            ViewTransform zv = viewFor(chapter, mapRect());
            zv.zoomAt(mouseX, mouseY, delta > 0 ? 1.15 : 1 / 1.15);
            sharedZoom = zv.zoom();
            return true;
        }
        return false;
    }

    @Override
    protected List<String> tooltip(int mouseX, int mouseY) {
        return null; // drawn by the widget itself, at the very end of its drawing
    }

    private List<String> computeTooltip(int mouseX, int mouseY) {
        Quest hovered = questAt(mouseX, mouseY);
        hoverQuest = hovered == null ? "" : hovered.id;
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        ProgressEngine engine = ClientQuests.engine();
        if (book == null || p == null || engine == null || pressedOnMap && moved) {
            return null;
        }
        boolean focused = detailOpen() && !searching;
        for (Hit h : hits) {
            if (h.tip() != null && h.rect().contains(mouseX, mouseY) && (!focused || insideFocusWindow(mouseX, mouseY))) {
                return List.of(h.tip().split("\n"));
            }
        }
        if (detailOpen() || searching) {
            return null;
        }
        Quest q = questAt(mouseX, mouseY);
        if (q == null) {
            if (hoverImage.isEmpty() || !movingImage.isEmpty()) {
                return null;
            }
            Chapter here = currentChapter(book);
            for (Definitions.ChapterImage img : here == null ? List.<Definitions.ChapterImage>of() : here.images) {
                if (img.id().equals(hoverImage)) {
                    String location = img.raw().getString("image", "");
                    List<String> out = new ArrayList<>();
                    DataNode hover = img.raw().get("hover");
                    if (hover instanceof DataNode.Lst list) {
                        for (DataNode line : list.items()) {
                            if (line instanceof DataNode.Str str) {
                                out.add(RichText.toRainbowAware(str.value()));
                            }
                        }
                    } else if (hover instanceof DataNode.Str str && !str.value().isEmpty()) {
                        for (String line : str.value().split("\n")) {
                            out.add(RichText.toRainbowAware(line));
                        }
                    }
                    if (out.isEmpty()) {
                        String file = location.substring(location.lastIndexOf('/') + 1);
                        out.add(RichText.toRainbowAware(file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file));
                    }
                    if (isEditing()) {
                        out.add("§7" + location); // where the picture comes from is only of interest to an editor
                    }
                    return out;
                }
            }
            return null;
        }
        List<String> lines = new ArrayList<>();
        boolean details = QuestRules.detailsVisible(book, p, q);
        if (Keyboard.isKeyDown(Keyboard.KEY_F1)) {
            // F1 held: extended quest info: the id, when the quest was started and when it was completed
            String idShown = linkIds.containsKey(q) ? (isEditing() ? linkIds.get(q) : null) : q.id;
            if (idShown != null) {
                lines.add("\u00a78" + idShown);
            }
            java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.ENGLISH);
            Long startedAt = p.startedAt(q.id);
            Long completedAt = p.completedAt(q.id);
            if (startedAt != null) {
                lines.add("\u00a7fStarted: \u00a77" + when.format(new java.util.Date(startedAt)));
            }
            if (completedAt != null) {
                lines.add("\u00a7fCompleted: \u00a77" + when.format(new java.util.Date(completedAt)));
            }
        }
        lines.add("§f" + (details ? QuestText.title(q) : "???")); // white unless the title has colours of its own
        if (details && !q.subtitle.isEmpty()) {
            lines.add("§7" + RichText.toRainbowAware(q.subtitle)); // gray unless the subtitle has colours of its own
        }
        if (q.optional) {
            lines.add("\u00a77[Optional Quest]");
        }
        if (QuestRules.repeatable(book, q)) {
            lines.add("\u00a77[Repeatable Quest]");
        }
        State s = stateOf(book, engine, p, q);
        if (s != State.DONE && !QuestRules.dependenciesMet(book, p, q)) {
            lines.add("\u00a77[Locked: uncompleted dependencies]");
        }
        return lines;
    }


    /** Keys for everyone: Tab and Shift + Tab change chapter, Space centres the map again. */
    private boolean generalKey(int keyCode) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return false;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            List<Chapter> chapters = visibleChapters(book);
            if (chapters.isEmpty()) {
                return true;
            }
            int at = 0;
            for (int i = 0; i < chapters.size(); i++) {
                if (chapters.get(i).id.equals(chapterId)) {
                    at = i;
                }
            }
            int next = (at + (GuiScreen.isShiftKeyDown() ? -1 : 1) + chapters.size()) % chapters.size();
            chapterId = chapters.get(next).id;
            selectedQuest = "";
            return true;
        }
        if (!GuiScreen.isCtrlKeyDown() && (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
                || keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN)) {
            Chapter chapter = currentChapter(book);
            if (chapter != null) {
                // the camera moves one grid step (half a unit); the rim under the mouse follows because it snaps to the grid
                Rect map = mapRect();
                ViewTransform v = viewFor(chapter, map);
                double mx = map.x() + map.width() / 2.0;
                double my = map.y() + map.height() / 2.0;
                double dx = keyCode == Keyboard.KEY_LEFT ? -0.5 : keyCode == Keyboard.KEY_RIGHT ? 0.5 : 0;
                double dy = keyCode == Keyboard.KEY_UP ? -0.5 : keyCode == Keyboard.KEY_DOWN ? 0.5 : 0;
                v.centerOn(v.worldX(mx) + dx * GRID, v.worldY(my) + dy * GRID, mx, my);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_SPACE && !chapterId.isEmpty()) {
            VIEWS.remove(chapterId); // the chapter is centred again as when it was first opened
            return true;
        }
        return false;
    }

    /** Editor keys for the quests picked with Ctrl + click. @return true if the key was used */
    private boolean selectionKey(int keyCode) {
        QuestBook book = ClientQuests.book();
        Chapter chapter = book == null ? null : currentChapter(book);
        if (chapter == null) {
            return false;
        }
        syncSelection(chapter);
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        SELECTION.removeIf(id -> chapter.quests.stream().noneMatch(q -> q.id.equals(id)) && book.quest(id) == null);
        if (ctrl && keyCode == Keyboard.KEY_A) {
            chapter.quests.forEach(q -> SELECTION.add(q.id));
            return true;
        }
        if (!SELECTION.isEmpty() && (keyCode == Keyboard.KEY_ESCAPE || ctrl && keyCode == Keyboard.KEY_D)) {
            SELECTION.clear();
            return true;
        }
        if (ctrl && !SELECTION.isEmpty() && (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
                || keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN)) {
            // Ctrl + arrows: the picked quests move one grid step (half a unit, as when they snap)
            double stepX = keyCode == Keyboard.KEY_LEFT ? -0.5 : keyCode == Keyboard.KEY_RIGHT ? 0.5 : 0;
            double stepY = keyCode == Keyboard.KEY_UP ? -0.5 : keyCode == Keyboard.KEY_DOWN ? 0.5 : 0;
            List<DataNode> moves = new ArrayList<>();
            for (Quest q : chapter.quests) {
                if (SELECTION.contains(q.id)) {
                    moves.add(DataNode.Compound.empty().with("id", q.id).with("x", q.x + stepX).with("y", q.y + stepY));
                }
            }
            if (!moves.isEmpty()) {
                send("move_quests", DataNode.Compound.empty().with("moves", new DataNode.Lst(moves)));
            }
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_C) {
            COPIED.clear();
            if (!SELECTION.isEmpty()) {
                COPIED.addAll(SELECTION);
            } else if (!hoverQuest.isEmpty()) {
                COPIED.add(hoverQuest);
            }
            return !COPIED.isEmpty();
        }
        if (ctrl && keyCode == Keyboard.KEY_V && !COPIED.isEmpty()
                && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU))) {
            // + Alt: paste as quest links
            ViewTransform lv = viewFor(chapter, mapRect());
            double lx = Math.round(lv.worldX(lastMouseX) / GRID * 2.0) / 2.0;
            double ly = Math.round(lv.worldY(lastMouseY) / GRID * 2.0) / 2.0;
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            for (String cid : COPIED) {
                Quest src = book.quest(cid);
                if (src != null) {
                    minX = Math.min(minX, src.x);
                    minY = Math.min(minY, src.y);
                }
            }
            for (String cid : COPIED) {
                Quest src = book.quest(cid);
                if (src != null) {
                    send("create_link", args("chapter", chapter.id, "quest", cid, "x", lx + (src.x - minX), "y", ly + (src.y - minY)));
                }
            }
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_V && !COPIED.isEmpty()) {
            ViewTransform v = viewFor(chapter, mapRect());
            double px = Math.round(v.worldX(lastMouseX) / GRID * 2.0) / 2.0;
            double py = Math.round(v.worldY(lastMouseY) / GRID * 2.0) / 2.0;
            List<DataNode> ids = new ArrayList<>();
            COPIED.forEach(id -> ids.add(new DataNode.Str(id)));
            // Shift: paste without the dependencies on quests that were not copied too
            send("paste_quests", args("chapter", chapter.id, "x", px, "y", py, "deps", !GuiScreen.isShiftKeyDown())
                    .with("ids", new DataNode.Lst(ids)));
            return true;
        }
        boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        if (alt && !hoverQuest.isEmpty() && (keyCode == Keyboard.KEY_C || keyCode == Keyboard.KEY_R)) {
            // Alt + C / Alt + R: force-complete or force-reset the quest under the mouse
            send(keyCode == Keyboard.KEY_C ? "complete_quest" : "reset_progress", args("id", hoverQuest));
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_R) {
            crosshairs = !crosshairs; // Toggle Crosshairs
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_S) {
            send("save_now", DataNode.Compound.empty()); // Save Quests Now
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE && (!SELECTION.isEmpty() || !hoverQuest.isEmpty())) {
            final List<String> doomed = new ArrayList<>(SELECTION);
            if (doomed.isEmpty()) {
                doomed.add(hoverQuest); // nothing picked: the quest under the mouse
            }
            Runnable doDelete = () -> {
                List<DataNode> doomedIds = new ArrayList<>();
                doomed.forEach(id -> doomedIds.add(new DataNode.Str(id)));
                send("delete_quests", DataNode.Compound.empty().with("ids", new DataNode.Lst(doomedIds)));
                SELECTION.clear();
                selectedQuest = "";
            };
            if (GuiScreen.isShiftKeyDown()) {
                doDelete.run(); // Shift + Delete: no question asked
            } else {
                openMenu(lastMouseX, lastMouseY,
                        new MenuItem("Delete " + doomed.size() + " quest" + (doomed.size() == 1 ? "" : "s") + "?", "", "lib:remove", true, doDelete),
                        new MenuItem("Keep them", "", "", false, () -> { }));
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKey(char typed, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && !menu.isEmpty()) {
            menu = List.of(); // Esc closes an open context menu first, as in FTB Quests
            return true;
        }
        if (!imgMode.isEmpty()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                endImageMode(false);
            }
            return true;
        }
        if (!resizeQuest.isEmpty()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                endResize(false);
            }
            return true;
        }
        if (!bezierQuest.isEmpty()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                endBezier(true);
            } else if (keyCode == Keyboard.KEY_ESCAPE) {
                endBezier(false);
            }
            return true;
        }
        if (boxing && keyCode == Keyboard.KEY_ESCAPE) {
            boxing = false; // the box is dropped
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE && (!linkFrom.isEmpty() || confirmDelete || confirmDeleteChapter)) {
            linkFrom = "";
            confirmDelete = false;
            confirmDeleteChapter = false;
            return true;
        }
        if (!movingImage.isEmpty() && keyCode == Keyboard.KEY_ESCAPE) {
            cancelMove();
            return true;
        }
        if (movingQuest != null && keyCode == Keyboard.KEY_ESCAPE) {
            endQuestMove(false);
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE && detailOpen() && inlineKind.isEmpty() && !searching) {
            selectedQuest = ""; // FTB Quests 26.1.2: Esc closes the open quest window first and leaves the book open
            return true;
        }
        if (isEditing() && inlineKind.isEmpty() && movingQuest == null && !searching && GuiScreen.isCtrlKeyDown()
                && (keyCode == Keyboard.KEY_Z || keyCode == Keyboard.KEY_Y)) {
            if (keyCode == Keyboard.KEY_Z) {
                undoEdit();
            } else {
                redoEdit();
            }
            return true;
        }
        if (isEditing() && inlineKind.isEmpty() && movingQuest == null && !searching && menu.isEmpty() && selectionKey(keyCode)) {
            return true;
        }
        if (inlineKind.isEmpty() && movingQuest == null && !searching && menu.isEmpty() && !detailOpen() && generalKey(keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK && inlineKind.isEmpty() && !searching && menu.isEmpty()) {
            if (!ClientPrefs.backspaceHistory) {
                // the preference is off: Backspace acts like Escape
                if (detailOpen()) {
                    selectedQuest = "";
                } else {
                    mc().displayGuiScreen(null);
                }
            } else if (detailOpen()) {
                // while a quest is open: back to the one viewed before it; with none, the panel closes
                if (!VIEWED.isEmpty()) {
                    VIEWED.remove(VIEWED.size() - 1);
                }
                selectedQuest = VIEWED.isEmpty() ? "" : VIEWED.get(VIEWED.size() - 1);
                viewedTop = selectedQuest;
            } else {
                mc().displayGuiScreen(null); // no panel: the quest book closes, as with Escape
            }
            return true;
        }
        if (!inlineKind.isEmpty()) {
            boolean shift = GuiScreen.isShiftKeyDown();
            if (keyCode == Keyboard.KEY_ESCAPE) {
                inlineKind = "";
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitInline();
            } else if (GuiScreen.isCtrlKeyDown()) {
                switch (keyCode) {
                    case Keyboard.KEY_Z -> INLINE.undo();
                    case Keyboard.KEY_Y -> INLINE.redo();
                    case Keyboard.KEY_A -> INLINE.selectAll();
                    case Keyboard.KEY_C -> {
                        if (INLINE.hasSelection()) {
                            GuiScreen.setClipboardString(INLINE.selected());
                        }
                    }
                    case Keyboard.KEY_X -> {
                        if (INLINE.hasSelection()) {
                            GuiScreen.setClipboardString(INLINE.selected());
                            INLINE.deleteSelection();
                        }
                    }
                    case Keyboard.KEY_V -> INLINE.insert(GuiScreen.getClipboardString().replace("\n", " "), false);
                    default -> { }
                }
            } else {
                switch (keyCode) {
                    case Keyboard.KEY_LEFT -> INLINE.left(shift);
                    case Keyboard.KEY_RIGHT -> INLINE.right(shift);
                    case Keyboard.KEY_HOME -> INLINE.setCaret(0, shift);
                    case Keyboard.KEY_END -> INLINE.setCaret(INLINE.text().length(), shift);
                    case Keyboard.KEY_BACK -> INLINE.backspace();
                    case Keyboard.KEY_DELETE -> INLINE.delete();
                    default -> {
                        if (ChatAllowedCharacters.isAllowedCharacter(typed)) {
                            INLINE.insert(String.valueOf(typed));
                        }
                    }
                }
            }
            return true;
        }
        if (isEditing() && detailOpen() && !searching && !GuiScreen.isCtrlKeyDown()) {
            Quest edited = ClientQuests.book() == null ? null : ClientQuests.book().quest(selectedQuest);
            if (edited != null) {
                MenuItem[] items = editMenu(edited);
                int pick = keyCode == Keyboard.KEY_T ? 0 : keyCode == Keyboard.KEY_S ? 1 : keyCode == Keyboard.KEY_D ? 2
                        : keyCode == Keyboard.KEY_L ? 3 : keyCode == Keyboard.KEY_P ? 4 : keyCode == Keyboard.KEY_I ? 5
                        : keyCode == Keyboard.KEY_Q ? 6 : -1;
                if (pick >= 0) {
                    items[pick].action().run();
                    return true;
                }
            }
        }
        if (keyCode == Keyboard.KEY_P && GuiScreen.isCtrlKeyDown()) {
            mc().displayGuiScreen(new PreferencesScreen(mc().currentScreen));
            return true;
        }
        if (keyCode == Keyboard.KEY_F && GuiScreen.isCtrlKeyDown()) {
            toggleSearch();
            return true;
        }
        if (!searching) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            searching = false;
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!searchRows.isEmpty()) {
                pickRow(searchRows.get(0));
            }
        } else {
            RewardTablesScreen.typeInto(QUERY, typed, keyCode, false);
        }
        return true; // typing goes to the search box, not to the game
    }
}

/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;

@SideOnly(Side.CLIENT)
final class SelectorScreen extends GuiScreen {

    private static final int W = 185;
    private static final int H = 202;
    private static final int BASE_COLS = 9;
    private static final int CELL = 18;
    private static final Rect PREVIEW = new Rect(7, 18, 32, 32);
    private static final Rect COUNT = new Rect(48, 27, 46, 16);
    private static final Rect PLUS = new Rect(94, 23, 12, 12);
    private static final Rect MINUS = new Rect(94, 35, 12, 12);
    private static final Rect NBT0 = new Rect(110, 25, 20, 20);
    private static final Rect NBT_NO_COUNT = new Rect(44, 25, 20, 20);

    private Rect nbtR() {
        return askCount ? NBT0 : NBT_NO_COUNT;
    }
    private static final Rect MODE = new Rect(7, 56, 20, 20);
    private static final Rect SEARCH = new Rect(29, 58, 100, 16);
    private static final Rect GRID0 = new Rect(3, 81, 166, 91);
    private static final Rect TRACK0 = new Rect(170, 81, 11, 91);
    private static final Rect ACCEPT0 = new Rect(50, 177, 62, 20);
    private static final Rect CANCEL0 = new Rect(117, 177, 62, 20);

    private String titleText() {
        if (images) {
            return selectedImage == null ? "\u00a77None" : idTitle(selectedImage.id());
        }
        return selectedItem.isEmpty() ? "\u00a7oNothing Selected" : selectedItem.getDisplayName();
    }

    private int extra() {
        int need = fontRenderer.getStringWidth(titleText()) + 14 - W;
        return need <= 0 ? 0 : (need + CELL - 1) / CELL * CELL;
    }

    private int cols() {
        return BASE_COLS + extra() / CELL;
    }

    private Rect gridR() {
        return new Rect(GRID0.x(), GRID0.y(), GRID0.width() + extra(), GRID0.height());
    }

    private Rect trackR() {
        return new Rect(TRACK0.x() + extra(), TRACK0.y(), TRACK0.width(), TRACK0.height());
    }

    private Rect acceptR() {
        return new Rect(ACCEPT0.x() + extra(), ACCEPT0.y(), ACCEPT0.width(), ACCEPT0.height());
    }

    private Rect cancelR() {
        return new Rect(CANCEL0.x() + extra(), CANCEL0.y(), CANCEL0.width(), CANCEL0.height());
    }

    private static final int TEXT = 0xFFD8DEE9;
    private static final int BG = 0xFF3B4252;
    private static final int PANEL = 0xFF2E3440;
    private static final int DARK = 0xFF1C1F27;
    private static final int LIGHT = 0xFF495366;

    private static final int MAX_ITEMS = 12000;
    private static List<ItemStack> allItems;

    private record Cell(ItemStack stack, ImageCatalog.Entry image, String fluid) {}

    private final GuiScreen parent;
    private final boolean images;
    private final boolean askCount;
    private final BiConsumer<ItemStack, Integer> itemPicked;
    private final Consumer<String> imagePicked;

    interface FluidChoice {
        void accept(String name, long amount, String nbt);
    }

    private FluidChoice fluidPicked;
    private String selectedNbt = "";
    private boolean hoverNbt;
    private boolean fluidMode;
    private String selectedFluid;

    private boolean allowEmpty;
    private int mode;
    private final TextEditor search = new TextEditor("", 60);
    private final TextEditor countText = new TextEditor("1", 10);
    private boolean searchFocused = true;
    private long count = 1;
    private ItemStack selectedItem = ItemStack.EMPTY;
    private ImageCatalog.Entry selectedImage;
    private int scroll;
    private boolean dragging;
    private List<Cell> shown = new ArrayList<>();
    private String shownFor = null;
    private Cell hovered;
    private boolean hoverMode;

    private SelectorScreen(GuiScreen parent, boolean images, boolean askCount, BiConsumer<ItemStack, Integer> itemPicked, Consumer<String> imagePicked) {
        this.parent = parent;
        this.images = images;
        this.askCount = askCount;
        this.itemPicked = itemPicked;
        this.imagePicked = imagePicked;
    }

    static SelectorScreen items(GuiScreen parent, Consumer<ItemStack> picked) {
        return new SelectorScreen(parent, false, false, (stack, n) -> picked.accept(stack), null);
    }

    static SelectorScreen items(GuiScreen parent, boolean askCount, BiConsumer<ItemStack, Integer> picked) {
        return new SelectorScreen(parent, false, askCount, picked, null);
    }

    static SelectorScreen fluids(GuiScreen parent, boolean askCount, FluidChoice picked) {
        SelectorScreen screen = new SelectorScreen(parent, false, askCount, null, null);
        screen.fluidMode = true;
        screen.fluidPicked = picked;
        screen.count = 1000;
        screen.countText.replaceAllText("1000");
        return screen;
    }

    SelectorScreen preselectItem(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            selectedItem = stack.copy();
            selectedItem.setCount(1);
        }
        return this;
    }

    SelectorScreen preselectFluid(String fluidName, long amount, String nbt) {
        selectedNbt = nbt == null ? "" : nbt;
        return preselectFluid(fluidName, amount);
    }

    SelectorScreen preselectFluid(String fluidName, long amount) {
        String bare = fluidName.startsWith("minecraft:") ? fluidName.substring("minecraft:".length()) : fluidName;
        ItemStack bucket = bucketOf(bare);
        if (!bucket.isEmpty()) {
            selectedFluid = bare;
            selectedItem = bucket;
        }
        count = Math.max(1, Math.min(Integer.MAX_VALUE, amount));
        countText.replaceAllText(String.valueOf(count));
        return this;
    }

    static String fluidLabel(String fluidName, long amount) {
        String bare = fluidName.startsWith("minecraft:") ? fluidName.substring("minecraft:".length()) : fluidName;
        ItemStack bucket = bucketOf(bare);
        return amount + "mB " + (bucket.isEmpty() ? bare : bucket.getDisplayName());
    }

    private static ItemStack bucketOf(String fluidName) {
        net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
        if (f == null) {
            return ItemStack.EMPTY;
        }
        ItemStack bucket = net.minecraftforge.fluids.FluidUtil.getFilledBucket(new net.minecraftforge.fluids.FluidStack(f, 1000));
        if (bucket.isEmpty()) {
            bucket = new ItemStack(Items.BUCKET);
        }
        bucket.setStackDisplayName(f.getLocalizedName(new net.minecraftforge.fluids.FluidStack(f, 1000)));
        return bucket;
    }

    private static void drawFluid(net.minecraft.client.Minecraft mc,String fluidName, double x, double y, double size) {
        net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
        if (f != null && QuestArt.fluid(f.getStill(), x, y, size, 0xFFFFFFFF)) {
            return;
        }
        String tex = fluidTexture(fluidName);
        if (tex != null) {
            ImageCatalog.draw(mc, tex, x, y, size);
        }
    }

    private static String fluidTexture(String fluidName) {
        net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
        ResourceLocation still = f == null ? null : f.getStill();
        return still == null ? null : still.getNamespace() + ":textures/" + still.getPath() + ".png";
    }

    private static String fluidMod(String fluidName) {
        String tex = fluidTexture(fluidName);
        String domain = tex == null ? "minecraft" : tex.substring(0, tex.indexOf(':'));
        if (domain.equals("minecraft")) {
            return "Minecraft";
        }
        net.minecraftforge.fml.common.ModContainer mod = net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get(domain);
        return mod != null ? mod.getName() : domain;
    }

    private static List<String> allFluids() {
        List<String> names = new ArrayList<>(net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().keySet());
        java.util.Collections.sort(names);

        for (String first : new String[] {"lava", "water"}) {
            if (names.remove(first)) {
                names.add(0, first);
            }
        }
        return names;
    }

    static SelectorScreen images(GuiScreen parent, Consumer<String> picked) {
        return new SelectorScreen(parent, true, false, null, picked);
    }

    SelectorScreen allowEmpty() {
        this.allowEmpty = true;
        return this;
    }

    private static synchronized List<ItemStack> everything() {
        if (allItems == null) {
            List<ItemStack> out = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                NonNullList<ItemStack> subs = NonNullList.create();
                try {
                    item.getSubItems(CreativeTabs.SEARCH, subs);
                } catch (RuntimeException ex) {
                    subs.clear();
                }
                if (subs.isEmpty() && item.getCreativeTab() != null) {
                    subs.add(new ItemStack(item));
                }
                for (ItemStack s : subs) {
                    if (!s.isEmpty() && out.size() < MAX_ITEMS) {
                        out.add(s);
                    }
                }
            }
            allItems = out;
        }
        return allItems;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private Rect win() {
        return new Rect((width - W - extra()) / 2, (height - H) / 2, W + extra(), H);
    }

    private static Rect at(Rect win, Rect r) {
        return new Rect(win.x() + r.x(), win.y() + r.y(), r.width(), r.height());
    }

    private List<Cell> cells() {
        String key = mode + "|" + search.text();
        if (key.equals(shownFor)) {
            return shown;
        }
        shownFor = key;
        String needle = search.text().toLowerCase(Locale.ROOT).strip();
        List<Cell> out = new ArrayList<>();
        if (fluidMode) {
            for (String name : allFluids()) {
                ItemStack bucket = bucketOf(name);
                if (!bucket.isEmpty() && (needle.isEmpty() || name.contains(needle) || bucket.getDisplayName().toLowerCase(Locale.ROOT).contains(needle))) {
                    out.add(new Cell(bucket, null, name));
                }
            }
        } else if (images) {
            out.add(new Cell(ItemStack.EMPTY, null, null));
            for (ImageCatalog.Entry e : ImageCatalog.all()) {
                if (needle.isEmpty() || e.id().contains(needle) || e.modName().toLowerCase(Locale.ROOT).contains(needle)) {
                    out.add(new Cell(ItemStack.EMPTY, e, null));
                }
            }
        } else if (mode == 0) {
            out.add(new Cell(ItemStack.EMPTY, null, null));
            for (ItemStack s : everything()) {
                if (matches(s, needle)) {
                    out.add(new Cell(s, null, null));
                }
            }
        } else if (mc.player != null) {
            List<ItemStack> inv = new ArrayList<>(mc.player.inventory.mainInventory);
            inv.addAll(mc.player.inventory.offHandInventory);
            for (ItemStack s : inv) {
                if (!s.isEmpty() && matches(s, needle)) {
                    out.add(new Cell(s, null, null));
                }
            }
        }
        shown = out;
        scroll = 0;
        return out;
    }

    private static boolean matches(ItemStack s, String needle) {
        return needle.isEmpty() || s.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)
                || String.valueOf(s.getItem().getRegistryName()).toLowerCase(Locale.ROOT).contains(needle);
    }

    private int totalCount() {
        if (fluidMode) {
            return allFluids().size();
        }
        if (images) {
            return ImageCatalog.all().size();
        }
        if (mode == 0) {
            return everything().size();
        }
        int n = 0;
        if (mc.player != null) {
            for (ItemStack s : mc.player.inventory.mainInventory) {
                n += s.isEmpty() ? 0 : 1;
            }
            for (ItemStack s : mc.player.inventory.offHandInventory) {
                n += s.isEmpty() ? 0 : 1;
            }
        }
        return n;
    }

    private String modeName() {
        return fluidMode ? "All Fluids" : images ? "All Images" : mode == 0 ? "All Items" : "Inventory";
    }

    private static void box(int x, int y, int w, int h, int fill, int topLeft, int bottomRight) {
        Gui.drawRect(x, y, x + w, y + h, fill);
        Gui.drawRect(x, y, x + w, y + 1, topLeft);
        Gui.drawRect(x, y, x + 1, y + h, topLeft);
        Gui.drawRect(x, y + h - 1, x + w, y + h, bottomRight);
        Gui.drawRect(x + w - 1, y, x + w, y + h, bottomRight);
    }

    private static void button(Rect r, boolean hover) {
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF000000);
        box(r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, BG, 0xFF4F5666, 0xFF2F3645);
        if (hover) {
            int x = r.x() + 1;
            int y = r.y() + 1;
            int w = r.width() - 2;
            int h = r.height() - 2;
            Gui.drawRect(x, y, x + w, y + 1, 0xFFFFFFFF);
            Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
            Gui.drawRect(x, y, x + 1, y + h, 0xFFFFFFFF);
            Gui.drawRect(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        }
    }

    private void field(Rect r, TextEditor edit, boolean focused, String placeholder) {
        box(r.x(), r.y(), r.width(), r.height(), 0xFF5E81AC, 0xFF506E93, 0xFF739ED2);
        String text = edit.text();
        int x = r.x() + 4;
        int y = r.y() + 4;
        int room = r.width() - 8;
        if (text.isEmpty() && !focused && placeholder != null) {
            fontRenderer.drawString("§o" + placeholder, x, y, 0xFF97ADC9);
            return;
        }
        int caret = Math.min(edit.caret(), text.length());
        int first = 0;
        while (first < caret && fontRenderer.getStringWidth(text.substring(first, caret)) > room - 6) {
            first++;
        }
        String visible = fontRenderer.trimStringToWidth(text.substring(first), room);
        if (edit.hasSelection() && focused) {
            int s0 = Math.max(first, edit.selStart());
            int s1 = Math.min(first + visible.length(), edit.selEnd());
            if (s1 > s0) {
                Gui.drawRect(x + fontRenderer.getStringWidth(text.substring(first, s0)), r.y() + 2,
                        x + fontRenderer.getStringWidth(text.substring(first, s1)), r.y() + r.height() - 2, 0xAA3A6FD0);
            }
        }
        fontRenderer.drawString(visible, x, y, TEXT);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + fontRenderer.getStringWidth(text.substring(first, Math.max(first, caret)));
            fontRenderer.drawString("_", cx, y, TEXT);
        }
    }

    private void drawImage(ImageCatalog.Entry e, double x, double y, double size) {
        ImageCatalog.draw(mc, e.id(), x, y, size);
    }

    private static String idTitle(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "§e" + id : "§6" + id.substring(0, colon + 1) + "§e" + id.substring(colon + 1);
    }

    private int viewHeight() {
        return GRID0.height() - 3;
    }

    private int maxScroll(List<Cell> list) {
        int rows = (list.size() + cols() - 1) / cols();
        return Math.max(0, rows * CELL + 2 - viewHeight());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks);
        }
        drawDefaultBackground();
        Rect win = win();
        int wx = win.x();
        int wy = win.y();
        hovered = null;
        hoverMode = false;
        hoverNbt = false;
        List<Cell> list = cells();
        scroll = Math.max(0, Math.min(maxScroll(list), scroll));

        final int ww = W + extra();
        Gui.drawRect(wx, wy, wx + ww, wy + H, 0xFF101010);
        box(wx + 1, wy + 1, ww - 2, H - 2, BG, LIGHT, DARK);

        String title = titleText();
        fontRenderer.drawString(fontRenderer.trimStringToWidth(title, ww - 14), wx + 7, wy + 6, TEXT);

        boolean showPreview = images || !selectedItem.isEmpty();
        if (showPreview) {
            Rect p = at(win, PREVIEW);
            box(p.x(), p.y(), p.width(), p.height(), PANEL, DARK, LIGHT);
            if (images) {
                if (selectedImage != null) {
                    drawImage(selectedImage, p.x() + 2, p.y() + 2, 28);
                }
            } else if (fluidMode && selectedFluid != null && fluidTexture(selectedFluid) != null) {
                drawFluid(mc, selectedFluid, p.x() + 1, p.y() + 1, 30);
            } else {
                GlStateManager.pushMatrix();
                GlStateManager.translate(p.x() + 0, p.y() + 0, 0);
                GlStateManager.scale(2, 2, 1);
                RenderHelper.enableGUIStandardItemLighting();
                itemRender.renderItemAndEffectIntoGUI(selectedItem, 0, 0);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
                GlStateManager.disableLighting();
            }
        }
        if (!images && !selectedItem.isEmpty() && askCount) {
            fontRenderer.drawString("×", wx + 41, wy + 31, TEXT);
            field(at(win, COUNT), countText, !searchFocused, null);
            for (Rect r : new Rect[] {PLUS, MINUS}) {
                Rect b = at(win, r);
                button(b, b.contains(mouseX, mouseY));
                int cx = b.x() + b.width() / 2;
                int cy = b.y() + b.height() / 2;
                Gui.drawRect(cx - 2, cy, cx + 3, cy + 1, TEXT);
                if (r == PLUS) {
                    Gui.drawRect(cx, cy - 2, cx + 1, cy + 3, TEXT);
                }
            }
        }

        if (!images && !selectedItem.isEmpty()) {
            Rect nb = at(win, nbtR());
            hoverNbt = nb.contains(mouseX, mouseY);
            button(nb, hoverNbt);
            renderItem(new ItemStack(Items.NAME_TAG), nb.x() + 2, nb.y() + 2);
        }

        Rect mb = at(win, MODE);
        hoverMode = mb.contains(mouseX, mouseY);
        button(mb, hoverMode);
        if (images) {
            if (!Ui.icon(Ui.LIBRARY, "textures/icons/art.png", mb.x() + 2, mb.y() + 2, 16, 16, 0xFFFFFFFF)) {
                renderItem(new ItemStack(Items.PAINTING), mb.x() + 2, mb.y() + 2);
            }
        } else {
            renderItem(new ItemStack(mode == 0 ? Items.COMPASS : Item.getItemFromBlock(net.minecraft.init.Blocks.CHEST)), mb.x() + 2, mb.y() + 2);
        }
        field(at(win, SEARCH), search, searchFocused, "Search...");

        Gui.drawRect(wx + 2, wy + 79, wx + 183 + extra(), wy + 80, 0xFF282D38);
        Gui.drawRect(wx + 2, wy + 80, wx + 183 + extra(), wy + 175, PANEL);
        Gui.drawRect(wx + 2, wy + 175, wx + 183 + extra(), wy + 176, 0xFF535864);
        Rect g = at(win, gridR());
        Gui.drawRect(g.x(), g.y(), g.x() + g.width(), g.y() + g.height(), 0xFF101010);
        Gui.drawRect(g.x() + 1, g.y() + 1, g.x() + g.width() - 1, g.y() + g.height() - 1, PANEL);
        Rect view = new Rect(g.x() + 1, g.y() + 1, g.width() - 2, g.height() - 2);
        RewardTablesScreen.scissor(mc, view);
        int first = Math.max(0, (scroll - 2) / CELL);
        int last = Math.min((list.size() + cols() - 1) / cols() - 1, (scroll + view.height()) / CELL + 1);
        for (int r = first; r <= last; r++) {
            for (int c = 0; c < cols(); c++) {
                int index = r * cols() + c;
                if (index >= list.size()) {
                    break;
                }
                Cell cell = list.get(index);
                int cx = wx + 5 + c * CELL;
                int cy = wy + 83 + r * CELL - scroll;
                box(cx, cy, CELL, CELL, PANEL, DARK, LIGHT);
                if (cell.fluid() != null) {
                    String tex = fluidTexture(cell.fluid());
                    if (tex != null) {
                        drawFluid(mc, cell.fluid(), cx + 1, cy + 1, 16);
                    }
                } else if (cell.image() != null) {
                    drawImage(cell.image(), cx + 1, cy + 1, 16);
                } else if (!cell.stack().isEmpty()) {
                    renderItem(cell.stack(), cx + 1, cy + 1);
                    if (mode == 1) {
                        itemRender.renderItemOverlayIntoGUI(fontRenderer, cell.stack(), cx + 1, cy + 1, null);
                        GlStateManager.disableLighting();
                    }
                }
                if (view.contains(mouseX, mouseY) && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                    Gui.drawRect(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0x40FFFFFF);
                    hovered = cell;
                }
            }
        }
        RewardTablesScreen.unscissor();
        drawScrollBar(win, list);

        for (Rect r : new Rect[] {acceptR(), cancelR()}) {
            Rect b = at(win, r);
            boolean accept = r.x() == acceptR().x();
            button(b, b.contains(mouseX, mouseY));
            String icon = accept ? "textures/icons/accept.png" : "textures/icons/cancel.png";
            if (!Ui.icon(Ui.LIBRARY, icon, b.x() + 4, b.y() + 2, 16, 16, 0xFFFFFFFF)) {
                fontRenderer.drawStringWithShadow(accept ? "✔" : "✘", b.x() + 8, b.y() + 8, accept ? 0xFF98C600 : 0xFFFF3F27);
            }
            fontRenderer.drawStringWithShadow(accept ? "Accept" : "Cancel", b.x() + 22, b.y() + 8, b.contains(mouseX, mouseY) ? 0xFFEBCB8B : TEXT);
        }

        if (at(win, acceptR()).contains(mouseX, mouseY)) {
            drawHoveringText("§7[⇧ + Enter]", mouseX, mouseY);
        } else if (at(win, cancelR()).contains(mouseX, mouseY)) {
            drawHoveringText("§7[ESC]", mouseX, mouseY);
        } else if (hoverNbt) {
            drawHoveringText(java.util.List.of("Edit NBT", "§7Left-click: quick string editor"), mouseX, mouseY);
        } else if (hoverMode) {
            drawHoveringText(java.util.List.of("List Mode", "§7" + modeName() + " §8[" + totalCount() + "]"), mouseX, mouseY);
        } else if (hovered != null && hovered.fluid() != null) {
            drawHoveringText(java.util.List.of(hovered.stack().getDisplayName(), "§9§o" + fluidMod(hovered.fluid())), mouseX, mouseY);
        } else if (hovered != null && hovered.image() != null) {
            drawHoveringText(java.util.List.of(idTitle(hovered.image().id()), "§9§o" + hovered.image().modName()), mouseX, mouseY);
        } else if (hovered != null && !hovered.stack().isEmpty()) {
            renderToolTip(hovered.stack(), mouseX, mouseY);
        }
    }

    private void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
    }

    private int thumbHeight(List<Cell> list) {
        int content = (list.size() + cols() - 1) / cols() * CELL + 2;
        return Math.max(7, Math.min(TRACK0.height() - 4, (TRACK0.height() - 4) * viewHeight() / Math.max(1, content)));
    }

    private void drawScrollBar(Rect win, List<Cell> list) {
        Rect t = at(win, trackR());
        Gui.drawRect(t.x(), t.y(), t.x() + t.width(), t.y() + t.height(), 0xFF101010);
        box(t.x() + 1, t.y() + 1, t.width() - 2, t.height() - 2, PANEL, DARK, LIGHT);
        int max = maxScroll(list);
        int th = thumbHeight(list);
        int travel = t.height() - 4 - th;
        int ty = t.y() + 2 + (max == 0 ? 0 : travel * scroll / max);
        int tx = t.x() + 2;
        Gui.drawRect(tx, ty, tx + 7, ty + th, 0xFF313745);
        Gui.drawRect(tx, ty, tx + 7, ty + 1, 0xFF5E6B84);
        Gui.drawRect(tx, ty + 1, tx + 6, ty + th - 1, 0xFF434C5E);
    }

    private void dragTo(int mouseY) {
        List<Cell> list = cells();
        Rect t = at(win(), trackR());
        int th = thumbHeight(list);
        int travel = Math.max(1, t.height() - 4 - th);
        double f = (mouseY - t.y() - 2 - th / 2.0) / travel;
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * maxScroll(list));
    }

    private void accept() {
        if (images) {
            if (selectedImage != null) {
                mc.displayGuiScreen(parent);
                imagePicked.accept(selectedImage.id());
                return;
            }
        } else if (fluidMode) {
            if (selectedFluid != null) {
                long amount = askCount ? count : 1000;
                mc.displayGuiScreen(parent);
                fluidPicked.accept(selectedFluid, amount, selectedNbt);
                return;
            }
        } else if (!selectedItem.isEmpty()) {
            ItemStack stack = selectedItem.copy();
            int n = (int) Math.min(Integer.MAX_VALUE, askCount ? count : 1);
            mc.displayGuiScreen(parent);
            itemPicked.accept(stack, n);
            return;
        }
        mc.displayGuiScreen(parent);
        if (allowEmpty) {
            if (images) {
                imagePicked.accept("");
            } else {
                itemPicked.accept(ItemStack.EMPTY, 0);
            }
        }
    }

    private long countCap() {
        return Integer.MAX_VALUE;
    }

    private void openNbtEditor() {
        String initial = fluidMode ? (selectedNbt.isEmpty() ? "{}" : selectedNbt)
                : selectedItem.hasTagCompound() ? selectedItem.getTagCompound().toString() : "{}";
        mc.displayGuiScreen(new QuickNbtScreen(this, initial, tag -> {
            String text = tag.isEmpty() ? "" : tag.toString();
            if (fluidMode) {
                selectedNbt = text;
            } else {
                selectedItem.setTagCompound(tag.isEmpty() ? null : tag);
            }
        }));
    }

    private void setCount(long n) {
        count = Math.max(1, Math.min(countCap(), n));
        countText.replaceAllText(String.valueOf(count));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Rect win = win();
        if (button != 0) {
            return;
        }
        if (!win.contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (at(win, acceptR()).contains(mouseX, mouseY)) {
            accept();
        } else if (at(win, cancelR()).contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
        } else if (!images && !selectedItem.isEmpty() && at(win, nbtR()).contains(mouseX, mouseY)) {
            openNbtEditor();
        } else if (at(win, MODE).contains(mouseX, mouseY)) {
            if (!images && !fluidMode) {
                mode = (mode + 1) % 2;
                scroll = 0;
            }
        } else if (at(win, SEARCH).contains(mouseX, mouseY)) {
            searchFocused = true;
        } else if (!images && !selectedItem.isEmpty() && askCount && at(win, COUNT).contains(mouseX, mouseY)) {
            searchFocused = false;
        } else if (!images && !selectedItem.isEmpty() && askCount && at(win, PLUS).contains(mouseX, mouseY)) {
            setCount(count > countCap() - 1000 ? countCap() : count + (isShiftKeyDown() ? (fluidMode ? 1000 : 10) : 1));
        } else if (!images && !selectedItem.isEmpty() && askCount && at(win, MINUS).contains(mouseX, mouseY)) {
            setCount(count - (isShiftKeyDown() ? (fluidMode ? 1000 : 10) : 1));
        } else if (at(win, trackR()).contains(mouseX, mouseY)) {
            dragging = true;
            dragTo(mouseY);
        } else if (hovered != null) {
            if (images) {
                selectedImage = hovered.image();
                searchFocused = false;
            } else {
                selectedFluid = hovered.fluid();
                selectedNbt = "";
                selectedItem = hovered.stack().isEmpty() ? ItemStack.EMPTY : hovered.stack().copy();
                if (!selectedItem.isEmpty() && mode == 1) {
                    selectedItem.setCount(1);
                }
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (dragging) {
            dragTo(mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            accept();
        } else if (searchFocused || images) {
            searchFocused = true;
            RewardTablesScreen.typeInto(search, typed, keyCode, false);
        } else {
            RewardTablesScreen.typeInto(countText, typed, keyCode, true);
            try {
                count = Math.max(1, Math.min(countCap(), Long.parseLong(countText.text().isBlank() ? "1" : countText.text())));
            } catch (NumberFormatException ex) {
                count = countText.text().length() > 18 ? countCap() : 1;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= (wheel > 0 ? 1 : -1) * CELL;
            scroll = Math.max(0, Math.min(maxScroll(cells()), scroll));
        }
    }
}

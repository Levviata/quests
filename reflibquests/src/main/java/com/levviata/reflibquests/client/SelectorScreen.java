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
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;

/** The item, fluid and image selector; the shared frame lives in {@link SelectorBase}. */
@SideOnly(Side.CLIENT)
final class SelectorScreen extends SelectorBase<SelectorScreen.Cell> {

    private static final Rect COUNT = new Rect(48, 27, 46, 16);
    private static final Rect PLUS = new Rect(94, 23, 12, 12);
    private static final Rect MINUS = new Rect(94, 35, 12, 12);
    private static final Rect NBT0 = new Rect(110, 25, 20, 20);
    private static final Rect NBT_NO_COUNT = new Rect(44, 25, 20, 20);

    private Rect nbtR() {
        return askCount ? NBT0 : NBT_NO_COUNT;
    }

    @Override
    protected String titleText() {
        if (images) {
            return selectedImage == null ? "\u00a77None" : idTitle(selectedImage.id());
        }
        return selectedItem.isEmpty() ? "\u00a7oNothing Selected" : selectedItem.getDisplayName();
    }

    private static final int MAX_ITEMS = 12000;
    private static List<ItemStack> allItems;

    record Cell(ItemStack stack, ImageCatalog.Entry image, String fluid) {}

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
    private final TextEditor countText = new TextEditor("1", 10);
    private long count = 1;
    private ItemStack selectedItem = ItemStack.EMPTY;
    private ImageCatalog.Entry selectedImage;

    private SelectorScreen(GuiScreen parent, boolean images, boolean askCount, BiConsumer<ItemStack, Integer> itemPicked, Consumer<String> imagePicked) {
        super(parent);
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
    protected List<Cell> buildCells(String needle) {
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
        return out;
    }

    private static boolean matches(ItemStack s, String needle) {
        return needle.isEmpty() || s.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)
                || String.valueOf(s.getItem().getRegistryName()).toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    protected int totalCount() {
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

    @Override
    protected String modeName() {
        return fluidMode ? "All Fluids" : images ? "All Images" : mode == 0 ? "All Items" : "Inventory";
    }

    @Override
    protected int modeCount() {
        return images || fluidMode ? 1 : 2;
    }

    @Override
    protected boolean searchOnly() {
        return images;
    }

    private void drawImage(ImageCatalog.Entry e, double x, double y, double size) {
        ImageCatalog.draw(mc, e.id(), x, y, size);
    }

    @Override
    protected boolean showPreview() {
        return images || !selectedItem.isEmpty();
    }

    @Override
    protected void drawPreview(Rect p) {
        if (images) {
            if (selectedImage != null) {
                drawImage(selectedImage, p.x() + 2, p.y() + 2, 28);
            }
        } else if (fluidMode && selectedFluid != null && fluidTexture(selectedFluid) != null) {
            drawFluid(mc, selectedFluid, p.x() + 1, p.y() + 1, 30);
        } else {
            renderItemBig(p, selectedItem);
        }
    }

    @Override
    protected void drawExtras(Rect win, int mouseX, int mouseY) {
        hoverNbt = false;
        if (!images && !selectedItem.isEmpty() && askCount) {
            fontRenderer.drawString("×", win.x() + 41, win.y() + 31, TEXT);
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
    }

    @Override
    protected boolean drawExtraTooltip(int mouseX, int mouseY) {
        if (hoverNbt) {
            drawHoveringText(java.util.List.of("Edit NBT", "§7Left-click: quick string editor"), mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    protected void drawModeIcon(Rect mb) {
        if (images) {
            if (!Ui.icon(Ui.LIBRARY, "textures/icons/art.png", mb.x() + 2, mb.y() + 2, 16, 16, 0xFFFFFFFF)) {
                renderItem(new ItemStack(Items.PAINTING), mb.x() + 2, mb.y() + 2);
            }
        } else {
            renderItem(new ItemStack(mode == 0 ? Items.COMPASS : Item.getItemFromBlock(net.minecraft.init.Blocks.CHEST)), mb.x() + 2, mb.y() + 2);
        }
    }

    @Override
    protected void drawCell(Cell cell, int cx, int cy) {
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
    }

    @Override
    protected void drawCellTooltip(Cell hovered, int mouseX, int mouseY) {
        if (hovered.fluid() != null) {
            drawHoveringText(java.util.List.of(hovered.stack().getDisplayName(), "§9§o" + fluidMod(hovered.fluid())), mouseX, mouseY);
        } else if (hovered.image() != null) {
            drawHoveringText(java.util.List.of(idTitle(hovered.image().id()), "§9§o" + hovered.image().modName()), mouseX, mouseY);
        } else if (!hovered.stack().isEmpty()) {
            renderToolTip(hovered.stack(), mouseX, mouseY);
        }
    }

    @Override
    protected void select(Cell hovered) {
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

    @Override
    protected boolean clickExtras(Rect win, int mouseX, int mouseY) {
        if (images || selectedItem.isEmpty()) {
            return false;
        }
        if (at(win, nbtR()).contains(mouseX, mouseY)) {
            openNbtEditor();
            return true;
        }
        if (!askCount) {
            return false;
        }
        if (at(win, COUNT).contains(mouseX, mouseY)) {
            searchFocused = false;
            return true;
        }
        if (at(win, PLUS).contains(mouseX, mouseY)) {
            setCount(count > countCap() - 1000 ? countCap() : count + (isShiftKeyDown() ? (fluidMode ? 1000 : 10) : 1));
            return true;
        }
        if (at(win, MINUS).contains(mouseX, mouseY)) {
            setCount(count - (isShiftKeyDown() ? (fluidMode ? 1000 : 10) : 1));
            return true;
        }
        return false;
    }

    @Override
    protected void typeExtras(char typed, int keyCode) {
        RewardTablesScreen.typeInto(countText, typed, keyCode, true);
        try {
            count = Math.max(1, Math.min(countCap(), Long.parseLong(countText.text().isBlank() ? "1" : countText.text())));
        } catch (NumberFormatException ex) {
            count = countText.text().length() > 18 ? countCap() : 1;
        }
    }

    @Override
    protected void accept() {
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
}

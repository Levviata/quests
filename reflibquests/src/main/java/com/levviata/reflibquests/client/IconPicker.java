/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Mouse;

import com.levviata.reflib.gui.Rect;
import com.levviata.reflib.api.data.DataNode;

@SideOnly(Side.CLIENT)
final class IconPicker {

    static final String KEEP = "Unchanged";
    static final String CLEAR = "Clear";
    static final java.util.List<String> ICON_CHOICES = java.util.List.of(KEEP, CLEAR);

    private IconPicker() {}

    static SettingsScreen.Entry decorate(SettingsScreen.Entry row, DataNode.Compound current) {
        row.pick = done -> open(done);
        row.formatted(v -> describe(v, current));
        return row;
    }

    static String describe(String value, DataNode.Compound current) {
        if (value.equals(CLEAR)) {
            return "None";
        }
        if (value.startsWith("image:")) {
            return value.substring(6);
        }
        if (value.startsWith("item:")) {
            String[] parts = value.substring(5).split("[|]", 3);
            Item item = Item.getByNameOrId(parts[0]);
            if (item != null) {
                ItemStack shown = new ItemStack(item, 1, parts.length < 2 ? 0 : parse(parts[1]));
                return shown.getDisplayName();
            }
            return parts[0];
        }
        if (current == null || current.entries().isEmpty()) {
            return "None";
        }
        String image = current.getString("image", "");
        if (!image.isEmpty()) {
            return image;
        }
        ItemStack stack = QuestText.stackOf(current);
        return stack == null || stack.isEmpty() ? "None" : stack.getDisplayName();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    static DataNode.Compound edit(String kind, String id, String value) {
        if (value.equals(KEEP) || value.equals("None")) {
            return null;
        }
        DataNode.Compound a = DataNode.Compound.empty().with("kind", kind).with("id", id);
        if (value.startsWith("image:")) {
            return a.with("mode", "image").with("image", value.substring(6));
        }
        if (value.startsWith("item:")) {

            String[] parts = value.substring(5).split("[|]", 3);
            DataNode.Compound item = DataNode.Compound.empty().with("id", parts[0]);
            if (parts.length > 1 && parse(parts[1]) > 0) {
                item = item.with("damage", parse(parts[1]));
            }
            if (parts.length > 2 && !parts[2].isBlank()) {
                DataNode.Compound nbt = com.levviata.reflibquests.model.BookEditor.parseNbt(parts[2]);
                if (nbt != null && !nbt.entries().isEmpty()) {
                    item = item.with("nbt", nbt);
                }
            }
            return a.with("mode", "item").with("item", item);
        }
        return a.with("mode", value.equals("Held Item") ? "held" : "clear");
    }

    private static void open(Consumer<String> done) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int x = Mouse.getX() / res.getScaleFactor();
        int y = (mc.displayHeight - Mouse.getY()) / res.getScaleFactor();
        mc.displayGuiScreen(new Menu(mc.currentScreen, x, y, done));
    }

    private static final class Menu extends GuiScreen {
        private static final int ROW = 13;
        private final GuiScreen parent;
        private final int anchorX;
        private final int anchorY;
        private final Consumer<String> done;

        Menu(GuiScreen parent, int x, int y, Consumer<String> done) {
            this.parent = parent;
            this.anchorX = x;
            this.anchorY = y;
            this.done = done;
        }

        private static final List<String> LABELS = List.of("Item Icon", "Image Icon");

        private Rect box() {
            int w = 8;
            for (String l : LABELS) {
                w = Math.max(w, fontRendererStatic().getStringWidth(l));
            }
            w += 16 + 12;
            int h = LABELS.size() * ROW + 4;
            return new Rect(Math.min(anchorX + 4, width - w - 2), Math.min(anchorY, height - h - 2), w, h);
        }

        private static net.minecraft.client.gui.FontRenderer fontRendererStatic() {
            return net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            if (parent != null) {
                parent.drawScreen(-1000, -1000, partialTicks);
            }
            Rect b = box();
            Ui.shadow(b);
            Ui.box(b, 0xFF2F343B);
            int y = b.y() + 2;
            for (int i = 0; i < LABELS.size(); i++) {
                Rect row = new Rect(b.x() + 1, y, b.width() - 2, ROW);
                boolean hover = row.contains(mouseX, mouseY);
                int tx = b.x() + 6;
                if (i == 0) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(tx, y + 1, 0);
                    GlStateManager.scale(0.75f, 0.75f, 1f);
                    RenderHelper.enableGUIStandardItemLighting();
                    itemRender.renderItemAndEffectIntoGUI(new ItemStack(Items.DIAMOND), 0, 0);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();
                    GlStateManager.disableLighting();
                } else {
                    Ui.icon(Ui.LIBRARY, "textures/icons/art.png", tx, y + 2, 12, 12, 0xFFFFFFFF);
                }
                fontRenderer.drawStringWithShadow(LABELS.get(i), tx + 16, y + 4, hover ? 0xFFFF55 : 0xFFFFFF);
                y += ROW;
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
            Rect b = box();
            if (button == 0 && b.contains(mouseX, mouseY)) {
                int i = (mouseY - b.y() - 2) / ROW;
                if (i == 0) {
                    mc.displayGuiScreen(SelectorScreen.items(parent, stack -> done.accept(stack.isEmpty() ? CLEAR
                            : "item:" + stack.getItem().getRegistryName() + (stack.getMetadata() > 0 || stack.hasTagCompound() ? "|" + stack.getMetadata() : "")
                            + (stack.hasTagCompound() && !stack.getTagCompound().isEmpty() ? "|" + stack.getTagCompound() : ""))).allowEmpty());
                    return;
                }
                if (i == 1) {
                    mc.displayGuiScreen(SelectorScreen.images(parent, id -> done.accept(id.isEmpty() ? CLEAR : "image:" + id)).allowEmpty());
                    return;
                }
            }
            mc.displayGuiScreen(parent);
        }

        @Override
        protected void keyTyped(char typed, int keyCode) throws IOException {
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(parent);
            }
        }
    }
}

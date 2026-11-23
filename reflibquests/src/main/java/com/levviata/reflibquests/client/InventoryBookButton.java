/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Mouse;

import com.levviata.reflibquests.ReflibQuestsMod;

@SideOnly(Side.CLIENT)
final class InventoryBookButton {

    private static final ResourceLocation ICON = new ResourceLocation(ReflibQuestsMod.ID, "textures/items/quest_book.png");
    private static final int SIZE = 18;

    private boolean editing;
    private int restoreScale;
    private boolean dragging;
    private int dragDx;
    private int dragDy;
    private ResourceLocation inverted;

    private static boolean supports(GuiScreen gui) {
        return gui instanceof GuiInventory || gui instanceof GuiContainerCreative;
    }

    private static final int MARGIN = 2;

    private int cachedWidth = 1;
    private int cachedHeight = 1;
    private int cachedFactor = 1;
    private int cachedX = MARGIN;
    private int cachedY = MARGIN;

    private void refresh(Minecraft mc) {
        ClientPrefs.ensureLoaded();
        ScaledResolution sr = new ScaledResolution(mc);
        cachedWidth = sr.getScaledWidth();
        cachedHeight = sr.getScaledHeight();
        cachedFactor = sr.getScaleFactor();
        double[] f = ClientPrefs.bookButtonAt(cachedFactor);
        cachedX = MARGIN + (int) Math.round(f[0] * Math.max(0, cachedWidth - SIZE - 2 * MARGIN));
        cachedY = MARGIN + (int) Math.round(f[1] * Math.max(0, cachedHeight - SIZE - 2 * MARGIN));
    }

    private int[] position() {
        return new int[] {cachedX, cachedY};
    }

    private void place(Minecraft mc, int x, int y) {
        double spanX = Math.max(1, cachedWidth - SIZE - 2 * MARGIN);
        double spanY = Math.max(1, cachedHeight - SIZE - 2 * MARGIN);
        ClientPrefs.setBookButton(cachedFactor, (x - MARGIN) / spanX, (y - MARGIN) / spanY);
        refresh(mc);
    }

    private static boolean over(int[] p, int mx, int my) {
        return mx >= p[0] && my >= p[1] && mx < p[0] + SIZE && my < p[1] + SIZE;
    }

    private void stopEditing(Minecraft mc) {
        if (!editing) {
            return;
        }
        editing = false;
        dragging = false;
        ClientPrefs.save();
        if (mc.gameSettings.guiScale != restoreScale) {
            mc.gameSettings.guiScale = restoreScale;
            mc.resize(mc.displayWidth, mc.displayHeight);
            refresh(mc);
        }
    }

    private void nextScale(Minecraft mc) {
        int max = 1;
        while (max < 10 && mc.displayWidth / (max + 1) >= 320 && mc.displayHeight / (max + 1) >= 240) {
            max++;
        }
        int next = cachedFactor + 1;
        mc.gameSettings.guiScale = next > max ? 1 : next;
        ClientPrefs.save();
        mc.resize(mc.displayWidth, mc.displayHeight);
        refresh(mc);
    }

    private ResourceLocation invertedIcon(Minecraft mc) {
        if (inverted == null) {
            try (InputStream in = mc.getResourceManager().getResource(ICON).getInputStream()) {
                BufferedImage src = ImageIO.read(in);
                BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < src.getHeight(); y++) {
                    for (int x = 0; x < src.getWidth(); x++) {
                        int c = src.getRGB(x, y);
                        out.setRGB(x, y, c & 0xFF000000 | ~c & 0x00FFFFFF);
                    }
                }
                inverted = mc.getTextureManager().getDynamicTextureLocation("reflibquests_book_inverted", new DynamicTexture(out));
            } catch (IOException | RuntimeException ex) {
                inverted = ICON;
            }
        }
        return inverted;
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (!supports(gui)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int button = Mouse.getEventButton();
        if (button < 0) {
            return;
        }
        boolean down = Mouse.getEventButtonState();
        int mx = Mouse.getEventX() * cachedWidth / mc.displayWidth;
        int my = cachedHeight - Mouse.getEventY() * cachedHeight / mc.displayHeight - 1;
        int[] p = position();

        if (button == 0 && !down && dragging) {
            dragging = false;
            ClientPrefs.save();
            event.setCanceled(true);
            return;
        }
        if (!over(p, mx, my)) {
            return;
        }
        if (button == 2 && down) {
            if (GuiScreen.isShiftKeyDown()) {
                if (editing) {
                    stopEditing(mc);
                } else {
                    editing = true;
                    restoreScale = mc.gameSettings.guiScale;
                }
            } else if (editing && org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU)
                    || editing && org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RMENU)) {
                nextScale(mc);
            } else {
                return;
            }
            event.setCanceled(true);
        } else if (button == 1 && down && editing && GuiScreen.isShiftKeyDown()) {
            dragging = false;
            ClientPrefs.resetBookButton(cachedFactor);
            ClientPrefs.save();
            refresh(mc);
            event.setCanceled(true);
        } else if (button == 0 && down) {
            if (editing) {
                dragging = true;
                dragDx = mx - p[0];
                dragDy = my - p[1];
            } else {
                ClientBootstrap.open();
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!supports(gui)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (dragging) {
            if (Mouse.isButtonDown(0)) {
                place(mc, event.getMouseX() - dragDx, event.getMouseY() - dragDy);
            } else {
                dragging = false;
                ClientPrefs.save();
            }
        }
        int[] p = position();
        boolean hover = over(p, event.getMouseX(), event.getMouseY());
        if (hover || editing) {
            Gui.drawRect(p[0], p[1], p[0] + SIZE, p[1] + SIZE, editing ? 0x40FFFF55 : 0x40FFFFFF);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300);
        mc.getTextureManager().bindTexture(editing ? invertedIcon(mc) : ICON);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        Gui.drawModalRectWithCustomSizedTexture(p[0] + 1, p[1] + 1, 0, 0, 16, 16, 16, 16);
        if (editing) {
            String number = String.valueOf(cachedFactor);
            GlStateManager.pushMatrix();
            GlStateManager.translate(p[0] + SIZE - 1 - mc.fontRenderer.getStringWidth(number) * 0.75f, p[1] + SIZE - 1 - 9 * 0.75f, 0);
            GlStateManager.scale(0.75f, 0.75f, 1f);
            mc.fontRenderer.drawStringWithShadow(number, 0, 0, 0xFFFF55);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
        if (hover && !dragging) {
            List<String> tip = new ArrayList<>();
            tip.add("Quest Book");
            if (!GuiScreen.isShiftKeyDown() && !org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU)
                    && !org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RMENU)) {
                tip.add("§8Shift/Alt to open controls");
            } else if (editing) {
                tip.add("§eLeft mouse button§7 drag to move the icon at this GUI scale");
                tip.add("§eShift + Right-click§7 to reset this GUI scale");
                tip.add("§eAlt + Middle-click§7 to go to the next GUI scale");
                tip.add("§eShift + Middle-click§7 to stop moving the icon");
            } else {
                tip.add("§eLeft-click§7 to open the quest book");
                tip.add("§eShift + Middle-click§7 to move this icon");
            }
            gui.drawHoveringText(tip, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (supports(event.getGui())) {
            refresh(Minecraft.getMinecraft());
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (editing && !supports(mc.currentScreen)) {
            stopEditing(mc);
        }
        if (supports(mc.currentScreen)) {
            refresh(mc);
        }
    }
}

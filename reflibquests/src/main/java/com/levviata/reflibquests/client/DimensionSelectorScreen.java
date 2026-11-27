/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.gui.Rect;
import com.levviata.reflibquests.io.Names;

/**
 * Picks a dimension. Built like the image selector: choose a cell, confirm with Accept; the header shows the
 * dimension's name, the search bar filters, Cancel leaves everything as it was.
 */
@SideOnly(Side.CLIENT)
final class DimensionSelectorScreen extends SelectorBase<DimensionSelectorScreen.Cell> {

    /** {@code id} is the value stored in the quest, {@code label} is what is shown. */
    record Cell(String id, String label, ItemStack icon) {}

    private final Consumer<String> picked;
    private final List<Cell> all = new ArrayList<>();
    private Cell selected;

    private DimensionSelectorScreen(GuiScreen parent, Consumer<String> picked) {
        super(parent);
        this.picked = picked;
        for (String id : Names.dimensionNames()) {
            all.add(cellOf(id));
        }
    }

    /** {@code current} is the stored value of the task (may be empty or not one of the registered dimensions). */
    static DimensionSelectorScreen create(GuiScreen parent, String current, Consumer<String> picked) {
        DimensionSelectorScreen screen = new DimensionSelectorScreen(parent, picked);
        if (current != null && !current.isEmpty()) {
            for (Cell c : screen.all) {
                if (c.id().equalsIgnoreCase(current) || c.label().equalsIgnoreCase(Names.dimensionLabel(current))) {
                    screen.selected = c;
                    break;
                }
            }
            if (screen.selected == null) {
                screen.selected = cellOf(current);
                screen.all.add(screen.selected);
            }
        }
        return screen;
    }

    private static Cell cellOf(String id) {
        String label = Names.dimensionLabel(id);
        ItemStack icon;
        switch (label) {
            case "minecraft:overworld":
            case "overworld":
                icon = new ItemStack(Blocks.GRASS);
                break;
            case "minecraft:the_nether":
            case "the_nether":
                icon = new ItemStack(Blocks.NETHERRACK);
                break;
            case "minecraft:the_end":
            case "the_end":
                icon = new ItemStack(Blocks.END_STONE);
                break;
            default:
                icon = new ItemStack(Blocks.END_PORTAL_FRAME);
                break;
        }
        return new Cell(id, label, icon);
    }

    @Override
    protected String titleText() {
        return selected == null ? "\u00a77None" : idTitle(selected.label());
    }

    @Override
    protected List<Cell> buildCells(String needle) {
        List<Cell> out = new ArrayList<>();
        for (Cell c : all) {
            if (needle.isEmpty() || c.label().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(c);
            }
        }
        return out;
    }

    @Override
    protected int totalCount() {
        return all.size();
    }

    @Override
    protected String modeName() {
        return "All Dimensions";
    }

    @Override
    protected boolean searchOnly() {
        return true;
    }

    @Override
    protected void drawModeIcon(Rect mb) {
        renderItem(new ItemStack(Blocks.END_PORTAL_FRAME), mb.x() + 2, mb.y() + 2);
    }

    @Override
    protected void drawPreview(Rect p) {
        if (selected != null) {
            renderItemBig(p, selected.icon());
        }
    }

    @Override
    protected void drawCell(Cell cell, int cx, int cy) {
        renderItem(cell.icon(), cx + 1, cy + 1);
    }

    @Override
    protected void drawCellTooltip(Cell cell, int mouseX, int mouseY) {
        if (cell.label().startsWith("minecraft:")) {
            drawHoveringText(java.util.List.of(idTitle(cell.label()), "\u00a79\u00a7oMinecraft"), mouseX, mouseY);
        } else {
            drawHoveringText(idTitle(cell.label()), mouseX, mouseY);
        }
    }

    @Override
    protected void select(Cell cell) {
        selected = cell;
        searchFocused = false;
    }

    @Override
    protected void accept() {
        mc.displayGuiScreen(parent);
        if (selected != null) {
            picked.accept(selected.id());
        }
    }
}

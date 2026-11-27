/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.gui.Rect;

/**
 * Picks a block or an entity type. The List Mode is the kind of thing being picked (block, entity_type), the header
 * shows the chosen one's resource location. Used by the Interaction task's Match row (both modes) and by the Kill
 * task's Entity Type row (entities only).
 */
@SideOnly(Side.CLIENT)
final class RegistrySelectorScreen extends SelectorBase<RegistrySelectorScreen.Cell> {

    static final int BLOCK = 0;
    static final int ENTITY = 1;

    /** {@code any} marks the block that means "any block" (air). */
    record Cell(String id, String label, ItemStack icon, boolean any) {}

    /** Called with the interaction type ("block" or "entity_type") and the registry name. */
    interface Choice {
        void accept(String type, String id);
    }

    private static List<Cell> blocks;
    private static List<Cell> entities;

    private final Choice picked;
    private final boolean locked;
    private final Cell[] selected = new Cell[2];

    private RegistrySelectorScreen(GuiScreen parent, int startMode, boolean locked, Choice picked) {
        super(parent);
        this.mode = startMode;
        this.locked = locked;
        this.picked = picked;
    }

    /**
     * Match selector of the Interaction task. The List Mode starts on the stored interaction type; the tag types have
     * no meaning in 1.12.2, so they open on their block or entity list with nothing selected.
     */
    static RegistrySelectorScreen match(GuiScreen parent, String interactionType, String match, Choice picked) {
        String type = interactionType == null ? "block" : interactionType;
        RegistrySelectorScreen screen = new RegistrySelectorScreen(parent, type.startsWith("entity") ? ENTITY : BLOCK, false, picked);
        if (!type.endsWith("_tag")) {
            screen.preselect(screen.mode, match);
        }
        return screen;
    }

    /** Entity-only selector (Kill task). */
    static RegistrySelectorScreen entity(GuiScreen parent, String current, Consumer<String> picked) {
        RegistrySelectorScreen screen = new RegistrySelectorScreen(parent, ENTITY, true, (type, id) -> picked.accept(id));
        screen.preselect(ENTITY, current);
        return screen;
    }

    private void preselect(int which, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        int bracket = id.indexOf('[');
        String bare = (bracket < 0 ? id : id.substring(0, bracket)).strip();
        for (Cell c : list(which)) {
            if (c.id().equalsIgnoreCase(bare)) {
                selected[which] = c;
                return;
            }
        }
    }

    private static synchronized List<Cell> list(int which) {
        if (which == BLOCK) {
            if (blocks == null) {
                List<Cell> out = new ArrayList<>();
                for (Block block : ForgeRegistries.BLOCKS) {
                    ResourceLocation key = block.getRegistryName();
                    if (key == null) {
                        continue;
                    }
                    Item item = Item.getItemFromBlock(block);
                    ItemStack icon = item == Items.AIR ? new ItemStack(Blocks.BARRIER) : new ItemStack(item);
                    out.add(new Cell(key.toString(), block.getLocalizedName(), icon, key.toString().equals("minecraft:air")));
                }
                blocks = out;
            }
            return blocks;
        }
        if (entities == null) {
            List<Cell> out = new ArrayList<>();
            for (EntityEntry entry : ForgeRegistries.ENTITIES) {
                ResourceLocation key = entry.getRegistryName();
                if (key != null) {
                    out.add(new Cell(key.toString(), I18n.format("entity." + entry.getName() + ".name"), egg(key), false));
                }
            }
            out.sort(Comparator.comparing(c -> c.label().toLowerCase(Locale.ROOT)));
            entities = out;
        }
        return entities;
    }

    /** The spawn egg of an entity, or a name tag for entities that have none. */
    private static ItemStack egg(ResourceLocation key) {
        if (EntityList.ENTITY_EGGS.containsKey(key)) {
            ItemStack egg = new ItemStack(Items.SPAWN_EGG);
            ItemMonsterPlacer.applyEntityIdToItemStack(egg, key);
            return egg;
        }
        return new ItemStack(Items.NAME_TAG);
    }

    @Override
    protected String titleText() {
        Cell c = selected[mode];
        return c == null ? "\u00a77None" : idTitle(c.id());
    }

    @Override
    protected List<Cell> buildCells(String needle) {
        List<Cell> out = new ArrayList<>();
        for (Cell c : list(mode)) {
            if (needle.isEmpty() || c.id().contains(needle) || c.label().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(c);
            }
        }
        return out;
    }

    @Override
    protected int totalCount() {
        return list(mode).size();
    }

    @Override
    protected String modeName() {
        return mode == BLOCK ? "All Blocks" : "All Entities";
    }

    @Override
    protected int modeCount() {
        return locked ? 1 : 2;
    }

    @Override
    protected boolean searchOnly() {
        return true;
    }

    @Override
    protected void drawModeIcon(Rect mb) {
        renderItem(mode == BLOCK ? new ItemStack(Blocks.GRASS) : egg(new ResourceLocation("minecraft:pig")), mb.x() + 2, mb.y() + 2);
    }

    @Override
    protected void drawPreview(Rect p) {
        if (selected[mode] != null) {
            renderItemBig(p, selected[mode].icon());
        }
    }

    @Override
    protected void drawCell(Cell cell, int cx, int cy) {
        renderItem(cell.icon(), cx + 1, cy + 1);
    }

    @Override
    protected void drawCellTooltip(Cell cell, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        lines.add(cell.label());
        lines.add(idTitle(cell.id()));
        if (cell.any()) {
            lines.add("\u00a77Matches any block");
        }
        drawHoveringText(lines, mouseX, mouseY);
    }

    @Override
    protected void select(Cell cell) {
        selected[mode] = cell;
        searchFocused = false;
    }

    @Override
    protected void accept() {
        mc.displayGuiScreen(parent);
        Cell c = selected[mode];
        if (c != null) {
            picked.accept(mode == BLOCK ? "block" : "entity_type", c.id());
        }
    }
}

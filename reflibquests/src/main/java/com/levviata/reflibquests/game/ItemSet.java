/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.io.IdMap;

/**
 * The items an item task accepts. A plain item is a set of one. A "smart filter" item (upstream's way of writing
 * "any of these items", for example {@code or(item(a:b)item(c:d))}) is a set of several: every {@code item(...)} that is
 * not under a {@code not(...)}, plus the items of any {@code tag(...)} that has a matching ore dictionary name.
 */
public final class ItemSet {

    public static final String SMART_FILTER = "ftbfiltersystem:smart_filter";

    private ItemSet() {}

    /** @return true if the spec is a filter that can match several different items */
    public static boolean isFilter(@Nullable DataNode spec) {
        return spec instanceof DataNode.Compound c && c.getString("id", "").equals(SMART_FILTER);
    }

    /** The filter text of a smart filter spec, or an empty string. */
    public static String filterText(@Nullable DataNode spec) {
        if (!(spec instanceof DataNode.Compound c)) {
            return "";
        }
        DataNode.Compound comps = c.getCompound("components");
        return comps.getString("ftbfiltersystem:filter", c.getString("filter", ""));
    }

    /** A name for a filter with no title: a lone tag such as {@code minecraft:hoes} is called "Hoes"; otherwise empty. */
    public static String suggestedName(@Nullable DataNode spec) {
        String text = filterText(spec);
        if (text.startsWith("ore(") && text.indexOf(')') == text.length() - 1) {
            return "Any " + text.substring(4, text.length() - 1);
        }
        int open = text.indexOf("tag(");
        if (open < 0 || text.contains("item(") || text.indexOf("tag(", open + 1) >= 0) {
            return "";
        }
        int end = text.indexOf(')', open);
        if (end < 0) {
            return "";
        }
        String arg = text.substring(open + 4, end);
        String last = arg.substring(Math.max(arg.lastIndexOf(':'), arg.lastIndexOf('/')) + 1).replace('_', ' ');
        return last.isEmpty() ? "" : Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    /** Every item the spec accepts, in 1.12.2 terms; empty if none of them exist in this game. */
    public static List<ItemResolver.Resolved> resolveAll(@Nullable DataNode spec, IdMap map) {
        List<ItemResolver.Resolved> out = new ArrayList<>();
        if (!isFilter(spec)) {
            ItemResolver.Resolved one = ItemResolver.resolve(spec, map);
            if (one != null) {
                out.add(one);
            }
            return out;
        }
        String text = filterText(spec);
        java.util.ArrayDeque<Boolean> negated = new java.util.ArrayDeque<>(); // one entry per open bracket
        for (int i = 0; i < text.length(); i++) {
            if (text.startsWith("not(", i)) {
                negated.push(true);
                i += 3;
                continue;
            }
            boolean skip = !negated.isEmpty() && negated.peek();
            if (text.startsWith("ore(", i) && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))) {
                // ore(name): everything registered under that exact ore dictionary name
                int end = text.indexOf(')', i);
                if (end < 0) {
                    break;
                }
                String name = text.substring(i + 4, end);
                i = end;
                if (!skip) {
                    for (ItemStack s : OreDictionary.getOres(name)) {
                        if (!s.isEmpty()) {
                            ItemResolver.Resolved r = new ItemResolver.Resolved(s.getItem(), s.getMetadata(), 1);
                            if (!contains(out, r)) {
                                out.add(r);
                            }
                        }
                    }
                }
                continue;
            }
            if (text.startsWith("item(", i) || text.startsWith("tag(", i)) {
                boolean item = text.startsWith("item(", i);
                int end = text.indexOf(')', i);
                if (end < 0) {
                    break;
                }
                String arg = text.substring(i + (item ? 5 : 4), end);
                i = end;
                if (skip) {
                    continue;
                }
                if (item) {
                    int brace = arg.indexOf('{'); // item(id{nbt}) keeps only the id
                    ItemResolver.Resolved r = ItemResolver.resolve(new DataNode.Str(brace > 0 ? arg.substring(0, brace) : arg), map);
                    if (r != null && !contains(out, r)) {
                        out.add(r);
                    }
                } else {
                    boolean any = false;
                    for (ItemStack s : OreDictionary.getOres(tagToOre(arg))) {
                        if (!s.isEmpty()) {
                            any = true;
                            ItemResolver.Resolved r = new ItemResolver.Resolved(s.getItem(), s.getMetadata(), 1);
                            if (!contains(out, r)) {
                                out.add(r);
                            }
                        }
                    }
                    if (!any) {
                        for (Item it : toolTag(arg)) {
                            ItemResolver.Resolved r = new ItemResolver.Resolved(it, OreDictionary.WILDCARD_VALUE, 1);
                            if (!contains(out, r)) {
                                out.add(r);
                            }
                        }
                    }
                }
                continue;
            }
            char c = text.charAt(i);
            if (c == '(') {
                negated.push(skip);
            } else if (c == ')' && !negated.isEmpty()) {
                negated.pop();
            }
        }
        return out;
    }

    /**
     * Tool tags (such as {@code minecraft:hoes}) have no ore dictionary name in 1.12.2, so they are worked out from the
     * registered items: vanilla tool classes first, then anything whose name says what it is (modded hoes and the like).
     */
    static List<Item> toolTag(String tag) {
        String last = tag.toLowerCase(Locale.ROOT);
        last = last.substring(Math.max(last.lastIndexOf(':'), last.lastIndexOf('/')) + 1);
        String kind = switch (last) {
            case "hoes", "hoe" -> "hoe";
            case "swords", "sword" -> "sword";
            case "pickaxes", "pickaxe" -> "pickaxe";
            case "axes", "axe" -> "axe";
            case "shovels", "shovel" -> "shovel";
            default -> "";
        };
        List<Item> out = new ArrayList<>();
        if (kind.isEmpty()) {
            return out;
        }
        for (Item it : ForgeRegistries.ITEMS) {
            if (it.getRegistryName() == null) {
                continue;
            }
            String path = it.getRegistryName().getPath();
            boolean named = path.equals(kind) || path.endsWith("_" + kind) || path.startsWith(kind + "_");
            boolean typed = switch (kind) {
                case "hoe" -> it instanceof ItemHoe;
                case "sword" -> it instanceof ItemSword;
                case "pickaxe" -> it instanceof ItemPickaxe || it.getToolClasses(new ItemStack(it)).contains("pickaxe");
                case "axe" -> it instanceof ItemAxe || it.getToolClasses(new ItemStack(it)).contains("axe");
                default -> it instanceof ItemSpade || it.getToolClasses(new ItemStack(it)).contains("shovel");
            };
            if (typed || (named && it.isDamageable())) {
                out.add(it);
            }
        }
        return out;
    }

    private static boolean contains(List<ItemResolver.Resolved> list, ItemResolver.Resolved r) {
        for (ItemResolver.Resolved o : list) {
            if (o.item() == r.item() && o.meta() == r.meta()) {
                return true;
            }
        }
        return false;
    }

    /** {@code c:ingots/iron} becomes the ore dictionary name {@code ingotIron}; unknown shapes are passed on unchanged. */
    static String tagToOre(String tag) {
        String t = tag.contains(":") ? tag.substring(tag.indexOf(':') + 1) : tag;
        String[] parts = t.toLowerCase(Locale.ROOT).split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (i == 0 && p.endsWith("s") && p.length() > 3) {
                p = p.substring(0, p.length() - 1); // ingots -> ingot
            }
            sb.append(i == 0 ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1));
        }
        return sb.toString();
    }

    /** True if the stack is one of the accepted items. */
    public static boolean matches(List<ItemResolver.Resolved> set, ItemStack have) {
        for (ItemResolver.Resolved r : set) {
            if (r.matches(have)) {
                return true;
            }
        }
        return false;
    }
}

/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

public final class NbtBridge {

    private NbtBridge() {}

    public static NBTTagCompound toNbt(DataNode.Compound compound) {
        NBTTagCompound out = new NBTTagCompound();
        for (Map.Entry<String, DataNode> e : compound.entries().entrySet()) {
            out.setTag(e.getKey(), toTag(e.getValue()));
        }
        return out;
    }

    private static NBTBase toTag(DataNode node) {
        if (node instanceof DataNode.Str v) {
            return new NBTTagString(v.value());
        } else if (node instanceof DataNode.Int v) {
            return new NBTTagInt(v.value());
        } else if (node instanceof DataNode.Lng v) {
            return new NBTTagLong(v.value());
        } else if (node instanceof DataNode.Dbl v) {
            return new NBTTagDouble(v.value());
        } else if (node instanceof DataNode.Bool v) {
            return new NBTTagByte((byte) (v.value() ? 1 : 0));
        } else if (node instanceof DataNode.Compound v) {
            return toNbt(v);
        } else if (node instanceof DataNode.Lst v) {
            NBTTagList list = new NBTTagList();
            Class<?> type = null;
            for (DataNode item : v.items()) {
                if (type == null) {
                    type = item.getClass();
                } else if (type != item.getClass()) {
                    throw new IllegalArgumentException("NBT lists must be homogeneous; found "
                            + type.getSimpleName() + " and " + item.getClass().getSimpleName());
                }
                list.appendTag(toTag(item));
            }
            return list;
        }
        throw new IllegalArgumentException("Unsupported node " + node);
    }

    public static DataNode.Compound fromNbt(NBTTagCompound tag) {
        Map<String, DataNode> map = new LinkedHashMap<>();
        for (String key : tag.getKeySet()) {
            map.put(key, fromTag(tag.getTag(key)));
        }
        return new DataNode.Compound(map);
    }

    private static DataNode fromTag(NBTBase tag) {
        switch (tag.getId()) {
            case Constants.NBT.TAG_BYTE: {
                int v = ((NBTTagByte) tag).getByte();
                return v == 0 || v == 1 ? new DataNode.Bool(v == 1) : new DataNode.Int(v);
            }
            case Constants.NBT.TAG_SHORT:
            case Constants.NBT.TAG_INT:
                return new DataNode.Int(((net.minecraft.nbt.NBTPrimitive) tag).getInt());
            case Constants.NBT.TAG_LONG:
                return new DataNode.Lng(((NBTTagLong) tag).getLong());
            case Constants.NBT.TAG_FLOAT:
            case Constants.NBT.TAG_DOUBLE:
                return new DataNode.Dbl(((net.minecraft.nbt.NBTPrimitive) tag).getDouble());
            case Constants.NBT.TAG_STRING:
                return new DataNode.Str(((NBTTagString) tag).getString());
            case Constants.NBT.TAG_BYTE_ARRAY: {
                List<DataNode> items = new ArrayList<>();
                for (byte b : ((NBTTagByteArray) tag).getByteArray()) {
                    items.add(new DataNode.Int(b));
                }
                return new DataNode.Lst(items);
            }
            case Constants.NBT.TAG_INT_ARRAY: {
                List<DataNode> items = new ArrayList<>();
                for (int i : ((NBTTagIntArray) tag).getIntArray()) {
                    items.add(new DataNode.Int(i));
                }
                return new DataNode.Lst(items);
            }
            case Constants.NBT.TAG_LIST: {
                NBTTagList list = (NBTTagList) tag;
                List<DataNode> items = new ArrayList<>();
                for (int i = 0; i < list.tagCount(); i++) {
                    items.add(fromTag(list.get(i)));
                }
                return new DataNode.Lst(items);
            }
            case Constants.NBT.TAG_COMPOUND:
                return fromNbt((NBTTagCompound) tag);
            default:
                throw new IllegalArgumentException("Unsupported NBT tag type " + tag.getId());
        }
    }
}

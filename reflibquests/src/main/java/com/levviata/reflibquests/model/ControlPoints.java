/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.levviata.reflib.api.data.DataNode;

/** Reads and writes the Bezier control points of a quest's dependency lines ({@code dep_control_pts}). */
public final class ControlPoints {

    /** Points further out than this (in grid units) are not taken. */
    public static final double LIMIT = 100_000;

    private ControlPoints() {}

    /** @return the four numbers of a line's points, or null if the node is not a list of four finite numbers */
    public static List<Double> parse(DataNode n) {
        if (!(n instanceof DataNode.Lst l) || l.items().size() != 4) {
            return null;
        }
        List<Double> out = new ArrayList<>(4);
        for (DataNode item : l.items()) {
            double v;
            if (item instanceof DataNode.Dbl d) {
                v = d.value();
            } else if (item instanceof DataNode.Int i) {
                v = i.value();
            } else if (item instanceof DataNode.Lng g) {
                v = g.value();
            } else {
                return null;
            }
            if (!Double.isFinite(v) || Math.abs(v) > LIMIT) {
                return null;
            }
            out.add(v);
        }
        return out;
    }

    public static Map<String, List<Double>> read(DataNode.Compound c) {
        Map<String, List<Double>> out = new LinkedHashMap<>();
        for (Map.Entry<String, DataNode> e : c.entries().entrySet()) {
            List<Double> pts = parse(e.getValue());
            if (pts != null) {
                out.put(e.getKey(), pts);
            }
        }
        return out;
    }

    public static DataNode.Compound write(Map<String, List<Double>> points) {
        DataNode.Compound o = DataNode.Compound.empty();
        for (Map.Entry<String, List<Double>> e : points.entrySet()) {
            List<DataNode> nums = new ArrayList<>();
            e.getValue().forEach(v -> nums.add(new DataNode.Dbl(v)));
            o = o.with(e.getKey(), new DataNode.Lst(nums));
        }
        return o;
    }
}

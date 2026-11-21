/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

/**
 * Pan and zoom state for a canvas (such as a quest map). Pure logic, no game classes.
 * {@code screen = world * zoom + offset}; zooming keeps the world point under the pointer fixed.
 */
public final class ViewTransform {

    private final double minZoom;
    private final double maxZoom;
    private double zoom = 1.0;
    private double offsetX;
    private double offsetY;

    public ViewTransform(double minZoom, double maxZoom) {
        if (minZoom <= 0 || maxZoom < minZoom) {
            throw new IllegalArgumentException("invalid zoom range " + minZoom + ".." + maxZoom);
        }
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
    }

    public double zoom() {
        return zoom;
    }

    public double screenX(double worldX) {
        return worldX * zoom + offsetX;
    }

    public double screenY(double worldY) {
        return worldY * zoom + offsetY;
    }

    public double worldX(double screenX) {
        return (screenX - offsetX) / zoom;
    }

    public double worldY(double screenY) {
        return (screenY - offsetY) / zoom;
    }

    /** Move the view by a screen-space delta (dragging). */
    public void panBy(double dx, double dy) {
        offsetX += dx;
        offsetY += dy;
    }

    /** Put the world point (wx, wy) at the screen point (sx, sy) without changing zoom. */
    public void centerOn(double wx, double wy, double sx, double sy) {
        offsetX = sx - wx * zoom;
        offsetY = sy - wy * zoom;
    }

    /** Multiply the zoom by {@code factor} (clamped), keeping the world point under (sx, sy) in place. */
    public void zoomAt(double sx, double sy, double factor) {
        double wx = worldX(sx);
        double wy = worldY(sy);
        zoom = Math.max(minZoom, Math.min(maxZoom, zoom * factor));
        offsetX = sx - wx * zoom;
        offsetY = sy - wy * zoom;
    }
}

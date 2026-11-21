/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

/** Clamped vertical scroll state for content taller than its viewport. Pure logic, no game classes. */
public final class ScrollModel {

    private int contentHeight;
    private int viewportHeight;
    private int offset;

    public ScrollModel(int contentHeight, int viewportHeight) {
        resize(contentHeight, viewportHeight);
    }

    public void resize(int contentHeight, int viewportHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        this.viewportHeight = Math.max(0, viewportHeight);
        this.offset = clamp(offset);
    }

    public int maxOffset() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    public int offset() {
        return offset;
    }

    public void setOffset(int value) {
        offset = clamp(value);
    }

    public void scrollBy(int delta) {
        offset = clamp(offset + delta);
    }

    /** @return the share of the content that is visible, in (0, 1]; 1 when nothing needs scrolling */
    public double visibleFraction() {
        return contentHeight <= viewportHeight || contentHeight == 0 ? 1.0 : (double) viewportHeight / contentHeight;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(maxOffset(), value));
    }
}

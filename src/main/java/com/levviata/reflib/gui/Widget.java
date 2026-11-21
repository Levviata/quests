/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Base class of the client-side widget tree. Coordinates are absolute screen pixels. Containers pass input
 * to their topmost child that contains the pointer.
 */
@SideOnly(Side.CLIENT)
public abstract class Widget {

    protected Rect bounds;
    protected boolean visible = true;
    private final List<Widget> children = new ArrayList<>();

    protected Widget(Rect bounds) {
        this.bounds = bounds;
    }

    public Rect bounds() {
        return bounds;
    }

    public void setBounds(Rect bounds) {
        this.bounds = bounds;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public List<Widget> children() {
        return children;
    }

    public <W extends Widget> W add(W child) {
        children.add(child);
        return child;
    }

    /** Draw this widget, then its children. Override {@link #drawSelf} to paint. */
    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        drawSelf(mouseX, mouseY, partialTicks);
        for (Widget child : children) {
            child.draw(mouseX, mouseY, partialTicks);
        }
    }

    protected void drawSelf(int mouseX, int mouseY, float partialTicks) {}

    /** @return true if the click was consumed */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return onClick(mouseX, mouseY, button);
    }

    /** @return true if the scroll was consumed */
    public boolean mouseScrolled(int mouseX, int mouseY, int delta) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return onScroll(mouseX, mouseY, delta);
    }

    /**
     * The pointer moved with a button held. Delivered to the widget that consumed the press, then up its
     * children if it does not. {@code dx}/{@code dy} are the movement since the last call.
     *
     * @return true if consumed
     */
    public boolean mouseDragged(int mouseX, int mouseY, int button, int dx, int dy) {
        if (!visible) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseDragged(mouseX, mouseY, button, dx, dy)) {
                return true;
            }
        }
        return onDrag(mouseX, mouseY, button, dx, dy);
    }

    /** @return true if consumed */
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return onRelease(mouseX, mouseY, button);
    }

    /** @return true if the key was consumed (so the screen does not also handle it, e.g. Escape) */
    public boolean keyTyped(char typed, int keyCode) {
        if (!visible) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).keyTyped(typed, keyCode)) {
                return true;
            }
        }
        return onKey(typed, keyCode);
    }

    /** @return tooltip lines for the pointer position, or null for none */
    public java.util.List<String> tooltipAt(int mouseX, int mouseY) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            java.util.List<String> t = children.get(i).tooltipAt(mouseX, mouseY);
            if (t != null) {
                return t;
            }
        }
        return tooltip(mouseX, mouseY);
    }

    protected boolean onClick(int mouseX, int mouseY, int button) {
        return false;
    }

    protected boolean onScroll(int mouseX, int mouseY, int delta) {
        return false;
    }

    protected boolean onDrag(int mouseX, int mouseY, int button, int dx, int dy) {
        return false;
    }

    protected boolean onRelease(int mouseX, int mouseY, int button) {
        return false;
    }

    protected boolean onKey(char typed, int keyCode) {
        return false;
    }

    protected java.util.List<String> tooltip(int mouseX, int mouseY) {
        return null;
    }
}

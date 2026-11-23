/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.ChapterImage;

@SideOnly(Side.CLIENT)
final class ImagePropertiesScreen extends SettingsScreen {

    private static final List<String> ALIGN = List.of("Start", "Middle", "End");
    private static final List<String> ACTIONS = List.of("No Action", "Open a URI", "Jump to a Quest", "Run a Command",
            "Post Custom Event", "Show Recipes for Item", "Show a Docs Page");

    private static final List<String> ACTION_IDS = List.of("", "open_uri", "open_quest", "run_command", "custom_event", "show_recipe", "show_docs");
    private static final String CLICK_DATA_TIP = "▪ Open URI: a http://, https:// or file:/// URL\n"
            + "▪ Jump to a Quest: a quest ID - use 'Copy Quest ID' context menu entry\n"
            + "▪ Run a Command: command to run on the server\n"
            + "▪ Post Custom Event: ID argument (namespace:path) for CustomClickEvent listeners\n"
            + "▪ Show Recipes for Item: item ID\n"
            + "▪ Show a Docs Page: <mod>,<book-id>[,<page-id>[,<anchor>]]";

    private final ChapterImage image;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> initial = new LinkedHashMap<>();

    ImagePropertiesScreen(GuiScreen parent, ChapterImage img) {
        super(parent, titleOf(img));
        this.image = img;
        DataNode.Compound r = img.raw();
        values.put("title", r.getString("title", ""));
        StringBuilder tagText = new StringBuilder();
        for (DataNode t : r.getList("tags")) {
            tagText.append(tagText.length() > 0 ? ", " : "").append(t instanceof DataNode.Str ts ? ts.value() : "");
        }
        values.put("tags", tagText.toString());
        values.put("click_type", r.getCompound("click_action").getString("type", ""));
        values.put("click_data", r.getCompound("click_action").getString("data", ""));
        values.put("dependency", r.getString("dependency", ""));
        values.put("height", plain(r.getDouble("height", 1)));
        values.put("image", r.getString("image", ""));
        values.put("alpha", String.valueOf(r.getInt("alpha", 255)));
        values.put("order", String.valueOf(r.getInt("order", 0)));
        values.put("color", String.format(java.util.Locale.ROOT, "#%06X", r.has("color") ? r.getInt("color", 0xFFFFFF) & 0xFFFFFF : 0xFFFFFF));
        values.put("position_locked", String.valueOf(r.getBool("position_locked", false)));
        values.put("dev", String.valueOf(r.getBool("dev", false)));
        values.put("corner", String.valueOf(r.getBool("corner", false)));
        values.put("rotation", plain(r.getDouble("rotation", 0)));
        values.put("width", plain(r.getDouble("width", 1)));
        values.put("x", plain(r.getDouble("x", 0)));
        values.put("y", plain(r.getDouble("y", 0)));
        values.put("text_on_image", String.valueOf(r.getBool("text_on_image", false)));
        values.put("text_h_align", r.getString("text_h_align", "middle"));
        values.put("text_inset", String.valueOf(r.getInt("text_inset", 0)));
        values.put("text_v_align", r.getString("text_v_align", "middle"));
        initial.putAll(values);

        section("Image");
        text("Title", () -> values.get("title"), v -> values.put("title", v.strip())).tip("Custom title").showValue().defaults("");
        text("Tags", () -> values.get("tags"), v -> values.put("tags", v))
                .formatted(v -> {
                    int n = v.isBlank() ? 0 : v.split(",").length;
                    return "[ " + n + " item" + (n == 1 ? "" : "s") + " ]";
                }).plainText().tip("Tags separated by commas.").defaults("[ 0 items ]");
        List<String> actions = new java.util.ArrayList<>(ACTIONS);
        if (!ACTION_IDS.contains(values.get("click_type"))) {
            actions.add(values.get("click_type"));
        }
        choice("Click Action", actions,
                () -> ACTION_IDS.contains(values.get("click_type")) ? ACTIONS.get(ACTION_IDS.indexOf(values.get("click_type"))) : values.get("click_type"),
                v -> values.put("click_type", ACTIONS.contains(v) ? ACTION_IDS.get(ACTIONS.indexOf(v)) : v))
                .defaults("No Action").listing();
        text("Click Action Data", () -> values.get("click_data"), v -> values.put("click_data", v)).tip(CLICK_DATA_TIP).defaults("");
        text("Dependency", () -> values.get("dependency"), v -> values.put("dependency", v.strip())).plainText()
                .info(() -> "ID: " + (values.get("dependency").isEmpty() ? "null" : values.get("dependency")));
        number("Height", false, () -> values.get("height"), v -> values.put("height", plain(Math.max(0.0625, Math.min(200, Double.parseDouble(v)))))).defaults("1");
        text("Image", () -> values.get("image"), v -> values.put("image", v)).plainText().defaults("");
        number("Image Alpha", true, () -> values.get("alpha"), v -> values.put("alpha", String.valueOf(Math.max(0, Math.min(255, Integer.parseInt(v)))))).defaults("255");
        number("Image Ordering", true, () -> values.get("order"), v -> values.put("order", String.valueOf(Math.max(-1000, Math.min(1000, Integer.parseInt(v))))))
                .tip("Order in which images in the same chapter are drawn.\nHigher order images are drawn above lower orders.").defaults("0");
        text("Image Tint", () -> values.get("color"), v -> values.put("color", v.strip())).plainText().withSwatch().defaults("#FFFFFF");
        bool("Lock Position", () -> Boolean.parseBoolean(values.get("position_locked")), v -> values.put("position_locked", String.valueOf(v)))
                .tip("If true, prevents the image from being moved\nUseful to prevent accidental movement when other objects overlap").defaults("False");
        bool("Only visible in Editing Mode", () -> Boolean.parseBoolean(values.get("dev")), v -> values.put("dev", String.valueOf(v))).defaults("False");
        bool("Rotate and offset from corner", () -> Boolean.parseBoolean(values.get("corner")), v -> values.put("corner", String.valueOf(v))).defaults("False");
        number("Rotation", false, () -> values.get("rotation"), v -> values.put("rotation", plain(Double.parseDouble(v)))).defaults("0");
        number("Width", false, () -> values.get("width"), v -> values.put("width", plain(Math.max(0.0625, Math.min(200, Double.parseDouble(v)))))).defaults("1");
        number("X", false, () -> values.get("x"), v -> values.put("x", plain(Double.parseDouble(v)))).defaults("0");
        number("Y", false, () -> values.get("y"), v -> values.put("y", plain(Double.parseDouble(v)))).defaults("0");

        section("Image → Image Text Properties");
        bool("Draw Title Text on Image", () -> Boolean.parseBoolean(values.get("text_on_image")), v -> values.put("text_on_image", String.valueOf(v)))
                .tip("If true, title text (if any) is drawn directly on image\nIf false, title text is used a tooltip").defaults("False");
        choice("Text Horizontal Alignment", ALIGN, () -> cap(values.get("text_h_align")), v -> values.put("text_h_align", v.toLowerCase(java.util.Locale.ROOT)))
                .defaults("Middle").listing().enabledIf(() -> Boolean.parseBoolean(values.get("text_on_image")));
        number("Text Inset", true, () -> values.get("text_inset"), v -> values.put("text_inset", String.valueOf(Math.max(0, Math.min(50, Integer.parseInt(v))))))
                .tip("Horizontal & vertical insets as percentage of image width & height, respectively").defaults("0").range("0", "50")
                .enabledIf(() -> Boolean.parseBoolean(values.get("text_on_image")));
        choice("Text Vertical Alignment", ALIGN, () -> cap(values.get("text_v_align")), v -> values.put("text_v_align", v.toLowerCase(java.util.Locale.ROOT)))
                .defaults("Middle").listing().enabledIf(() -> Boolean.parseBoolean(values.get("text_on_image")));
    }

    private static String titleOf(ChapterImage img) {
        String t = img.raw().getString("title", "");
        return (t.isEmpty() ? "" : t + "§r ") + "§b[Image]";
    }

    private static String cap(String v) {
        return v.isEmpty() ? "Middle" : Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    private static String plain(double v) {
        return new java.math.BigDecimal(v).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    @Override
    protected void accepted() {
        DataNode.Compound props = DataNode.Compound.empty();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!e.getValue().equals(initial.get(e.getKey()))) {
                props = props.with(e.getKey(), e.getValue());
            }
        }
        if (!props.entries().isEmpty()) {
            TableEdits.send("set_image_props", DataNode.Compound.empty().with("id", image.id()).with("props", props));
        }
    }

    @Override
    protected void cancelled() {

    }
}

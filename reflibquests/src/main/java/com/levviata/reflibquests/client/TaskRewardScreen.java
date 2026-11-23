/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;

@SideOnly(Side.CLIENT)
final class TaskRewardScreen extends SettingsScreen {

    private static final List<String> CONSUME_LABELS = List.of("Default", "True", "False");
    private static final List<String> AUTO_LABELS = List.of("Default", "Disabled", "Enabled", "Enabled, No Toast", "Enabled, Invisible");
    private static final List<String> AUTO_VALUES = List.of("default", "disabled", "enabled", "no_toast", "invisible");

    private final Task task;
    private final Reward reward;
    private String title;
    private static final String ICON_NONE = "None";
    private String iconChoice = ChapterPropertiesScreen.ICON_KEEP;
    private String tags = "";
    private String initialTags = "";
    private String amount = "";
    private String optional = "False";
    private String consume = "Default";
    private String auto = "Default";

    private final java.util.Map<String, String> flags = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> initialFlags = new java.util.HashMap<>();
    private String team = "Default";
    private String exclude = "False";
    private String ignoreBlocking = "False";
    private String initialTeam = "Default";
    private String initialExclude = "False";
    private String initialIgnore = "False";
    private final String initialTitle;
    private final String initialAmount;
    private final String initialOptional;
    private final String initialConsume;
    private final String initialAuto;
    private final java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> initialExtra = new java.util.HashMap<>();
    private String box = "";
    private String initialBox = "";
    private String target = "";
    private String initialTarget = "";

    static String taskTypeName(String type) {
        switch (type) {
            case "item": return "Item";
            case "custom": return "Custom";
            case "xp": return "XP Levels";
            case "dimension": return "Visit Dimension";
            case "stat": return "Stat";
            case "kill": return "Kill Entity";
            case "location": return "Location";
            case "checkmark": return "Checkmark";
            case "advancement": return "Advancement";
            case "observation": return "Observation";
            case "biome": return "Visit Biome";
            case "structure": return "Find Structure";
            case "fluid": return "Fluid";
            case "energy": case "forge_energy": return "Forge Energy";
            case "gamestage": return "Stage";
            case "interaction": return "Interaction";
            default: return "Edit Task";
        }
    }

    record Draft(String chapter, double x, double y, DataNode.Compound taskArgs) {}

    record RewardDraft(String tableId, String entryId, String type, DataNode.Compound params) {}

    private RewardDraft rewardDraft;

    static TaskRewardScreen forRewardDraft(GuiScreen parent, RewardDraft draft) {
        TaskRewardScreen screen = new TaskRewardScreen(parent, null, new Reward(draft.entryId(), draft.type(), draft.params()), true);
        screen.rewardDraft = draft;
        return screen;
    }

    private Draft draft;
    private final List<DataNode.Compound> draftEdits = new java.util.ArrayList<>();

    static final String DRAFT_TASK = "@task";

    static TaskRewardScreen forDraft(GuiScreen parent, Draft draft) {
        DataNode.Compound a = draft.taskArgs();
        DataNode.Compound params = a.getCompound("params");
        if (a.getString("type", "").equals("item")) {
            params = DataNode.Compound.empty().with("item", a.getCompound("item")).with("count", a.getInt("count", 1));
        }
        TaskRewardScreen screen = new TaskRewardScreen(parent, new Task(DRAFT_TASK, a.getString("type", ""), params), null);
        screen.draft = draft;
        return screen;
    }

    private void emit(String op, DataNode.Compound args) {
        if (draft != null || rewardDraft != null) {
            draftEdits.add(DataNode.Compound.empty().with("op", op).with("args", args));
        } else {
            TableEdits.send(op, args);
        }
    }

    private boolean bigWindow;

    @Override
    protected float uiScale() {
        return 1f;
    }

    static TaskRewardScreen forTableEntry(GuiScreen parent, Reward reward) {
        return new TaskRewardScreen(parent, null, reward, true);
    }

    private static String boldTitle(String text) {
        return "§l" + text.replaceAll("§([0-9a-fA-F])", "§$1§l");
    }

    TaskRewardScreen(GuiScreen parent, @Nullable Task task, @Nullable Reward reward) {
        this(parent, task, reward, task == null && reward != null && ClientQuests.book() != null
                && ClientQuests.book().tables().values().stream().anyMatch(t -> t.entries.stream().anyMatch(e -> e.reward().id().equals(reward.id()))));
    }

    TaskRewardScreen(GuiScreen parent, @Nullable Task task, @Nullable Reward reward, boolean tableEntry) {
        super(parent, task != null ? "§n" + com.levviata.reflibquests.text.RichText.stripCodes(QuestText.taskTitle(task)) + "§r §9[Task]"
                : tableEntry ? boldTitle(QuestText.reward(reward))
                : "§n" + com.levviata.reflibquests.text.RichText.stripCodes(QuestText.reward(reward)) + "§r §d[Reward]");
        this.bigWindow = tableEntry;
        this.task = task;
        this.reward = reward;
        DataNode.Compound p = task != null ? task.params() : reward.params();
        title = p.getString("title", "");
        StringBuilder tagText = new StringBuilder();
        for (DataNode tn : p.getList("tags")) {
            if (tn instanceof DataNode.Str ts) {
                tagText.append(tagText.length() > 0 ? ", " : "").append(ts.value());
            }
        }
        tags = tagText.toString();
        initialTags = tags;
        if (task != null) {
            if (task.type().equals("item") || List.of("xp", "kill", "stat", "energy", "forge_energy").contains(task.type())) {
                amount = String.valueOf(task.maxProgress());
            }
            optional = task.optionalTask() ? "True" : "False";
            consume = !p.has("consume_items") ? "Default" : p.getBool("consume_items", false) ? "True" : "False";
        } else {
            String key = reward.type().equals("item") ? "count" : reward.type();
            if (reward.type().equals("item") || reward.type().equals("xp") || reward.type().equals("xp_levels")) {
                amount = String.valueOf(Math.max(1, p.getInt(key, 1)));
            }
            int at = AUTO_VALUES.indexOf(p.getString("auto", "default"));
            auto = AUTO_LABELS.get(Math.max(0, at));
            if (reward != null) {
                team = !p.has("team_reward") ? "Default" : p.getBool("team_reward", false) ? "True" : "False";
                exclude = p.getBool("exclude_from_claim_all", false) ? "True" : "False";
                ignoreBlocking = p.getBool("ignore_reward_blocking", false) ? "True" : "False";
                initialTeam = team;
                initialExclude = exclude;
                initialIgnore = ignoreBlocking;
            }
        }
        initialTitle = title;
        initialAmount = amount;
        initialOptional = optional;
        initialConsume = consume;
        initialAuto = auto;

        text("Title", () -> title, v -> title = v).tip("A name shown instead of the one made from what it is. Empty keeps the automatic name.");

        boolean hasIcon = !p.getCompound("icon").entries().isEmpty();
        iconChoice = ChapterPropertiesScreen.ICON_KEEP;
        IconPicker.decorate(choice("Icon", ChapterPropertiesScreen.ICON_CHOICES, () -> iconChoice, v -> iconChoice = v)
                .tip("The icon. None goes back to the automatic icon.").defaults("None"), p.getCompound("icon"));
        text("Tags", () -> tags, v -> tags = v).tip("Words that mark it, separated by commas.");
        if (!amount.isEmpty()) {
            number(task != null && (task.type().equals("item") || task.type().equals("kill")) ? "Count" : task != null && task.type().equals("stat") ? "Value" : "Amount", true, () -> amount,
                    v -> amount = String.valueOf(Math.max(1L, Long.parseLong(v)))).tip("How many are needed, or how many are given.");
        }
        String tk = task == null ? null : com.levviata.reflibquests.model.BookEditor.targetKey(task.type());
        if (task != null && task.type().equals("kill")) {

            extra.put("custom_name", p.getString("custom_name", ""));
            extra.put("entityTypeTag", p.getString("entityTypeTag", ""));
            extra.put("nbt_filter", p.getString("nbt_filter", ""));
            initialExtra.putAll(extra);
            text("Entity Name", () -> extra.get("custom_name"), v -> extra.put("custom_name", v)).tip("Only mobs with this custom name count. Empty counts any.");
        }
        if (tk != null) {
            target = p.getString(tk, "");
            initialTarget = target;
            boolean dimensionTask = task.type().equals("dimension");
            List<String> dimensions = new java.util.ArrayList<>(com.levviata.reflibquests.io.Names.dimensionNames());
            if (dimensionTask && !target.isEmpty() && !dimensions.contains(target)) {
                dimensions.add(target);
            }
            Entry targetRow = dimensionTask ? choice("Dimension", dimensions, () -> target, v -> target = v)
                    : text(task.type().equals("kill") ? "Entity Type" : task.type().equals("biome") ? "Biome"
                    : task.type().equals("structure") ? "Structure" : "Advancement", () -> target, v -> target = v);
            targetRow.tip(dimensionTask ? "The dimension that has to be visited. Click to go through every dimension this game has registered."
                    : "A registry name such as minecraft:plains.");
            if (task.type().equals("kill")) {

                targetRow.pick = done -> {
                    List<ListPickerScreen.Option> options = new java.util.ArrayList<>();
                    for (net.minecraftforge.fml.common.registry.EntityEntry entry : net.minecraftforge.fml.common.registry.ForgeRegistries.ENTITIES) {
                        net.minecraft.util.ResourceLocation key = entry.getRegistryName();
                        if (key != null) {
                            options.add(new ListPickerScreen.Option(net.minecraft.client.resources.I18n.format("entity." + entry.getName() + ".name") + " [" + key + "]", key.toString()));
                        }
                    }
                    options.sort(java.util.Comparator.comparing(ListPickerScreen.Option::label));
                    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                            new ListPickerScreen(net.minecraft.client.Minecraft.getMinecraft().currentScreen, "Select Entity Type", options, done));
                };
            }
        }
        if (task != null && task.type().equals("kill")) {
            text("Entity Type Tag", () -> extra.get("entityTypeTag"), v -> extra.put("entityTypeTag", v))
                    .tip("An entity type tag such as minecraft:boats. This game version has no entity type tags, so a tag alone matches nothing.");
            text("NBT Filter", () -> extra.get("nbt_filter"), v -> extra.put("nbt_filter", v)).tip("Only mobs whose data contains this (for example {Age:5}) count.");
        }
        if (task != null && task.type().equals("item") && p.get("item") instanceof DataNode.Compound itemSpec) {

            String nbtText = itemSpec.getCompound("nbt").entries().isEmpty() ? "" : "|" + com.levviata.reflib.api.data.Snbt.printCompact(itemSpec.getCompound("nbt"));
            extra.put("item", itemSpec.getString("id", "") + (itemSpec.getInt("damage", 0) > 0 || !nbtText.isEmpty() ? "|" + itemSpec.getInt("damage", 0) : "") + nbtText);
            initialExtra.put("item", extra.get("item"));
            Entry itemRow = text("Item", () -> extra.get("item"), v -> extra.put("item", v)).tip("The item that has to be handed in or collected.");
            itemRow.plain = true;
            itemRow.pick = done -> net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                    SelectorScreen.items(net.minecraft.client.Minecraft.getMinecraft().currentScreen, stack ->
                            done.accept(stack.getItem().getRegistryName() + (stack.getMetadata() > 0 || stack.hasTagCompound() ? "|" + stack.getMetadata() : "")
                                    + (stack.hasTagCompound() && !stack.getTagCompound().isEmpty() ? "|" + stack.getTagCompound() : ""))
                    ).preselectItem(stackOfRow(extra.get("item"))));
        }
        if (task != null && task.type().equals("fluid")) {

            List<String> names = new java.util.ArrayList<>();
            for (String fl : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().keySet()) {
                names.add("minecraft:" + fl);
            }
            java.util.Collections.sort(names);
            DataNode fnode = p.get("fluid");
            String fname = fnode instanceof DataNode.Compound fc ? fc.getString("fluid", "minecraft:water") : "minecraft:water";
            long famount = fnode instanceof DataNode.Compound fc2 ? fc2.getLong("amount", 1000) : 1000;
            extra.put("fluid", fname.contains(":") ? fname : "minecraft:" + fname);
            extra.put("fluid_amount", String.valueOf(famount));
            extra.put("fluid_nbt", fnode instanceof DataNode.Compound fc3 && !fc3.getCompound("nbt").entries().isEmpty()
                    ? com.levviata.reflib.api.data.Snbt.printCompact(fc3.getCompound("nbt")) : "");
            initialExtra.put("fluid_nbt", extra.get("fluid_nbt"));
            initialExtra.put("fluid", extra.get("fluid"));
            initialExtra.put("fluid_amount", extra.get("fluid_amount"));

            Entry fluidRow = choice("Fluid", names, () -> extra.get("fluid"), v -> extra.put("fluid", v))
                    .tip("The fluid that has to be handed in, and how many millibuckets (a bucket holds 1000).");
            fluidRow.plain = true;
            fluidRow.formatted(v -> SelectorScreen.fluidLabel(v, parseLong(extra.get("fluid_amount"), 1000)));
            fluidRow.pick = done -> net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                    SelectorScreen.fluids(net.minecraft.client.Minecraft.getMinecraft().currentScreen, true, (name, amount, nbt) -> {
                        extra.put("fluid_amount", String.valueOf(Math.max(1L, Math.min(Integer.MAX_VALUE, amount))));
                        extra.put("fluid_nbt", nbt == null ? "" : nbt);
                        done.accept("minecraft:" + name);
                    }).preselectFluid(extra.get("fluid"), parseLong(extra.get("fluid_amount"), 1000), extra.get("fluid_nbt")));

            String fluidMatch = p.getString("match_components", "default");
            flag("Match Data Components", "match_components", List.of("Default", "Fuzzy", "Exact"),
                    Character.toUpperCase(fluidMatch.charAt(0)) + fluidMatch.substring(1), "Default and Fuzzy need the fluid to contain the task's data; Exact needs it to match.");
        }
        if (task != null && task.type().equals("stat")) {

            List<String> labels = new java.util.ArrayList<>();
            for (String id : com.levviata.reflibquests.model.BookEditor.STATS) {
                labels.add(statLabel(id));
            }
            String current = p.getString("stat", "minecraft:mob_kills");
            extra.put("stat", statLabel(current.contains(":") ? current.substring(current.indexOf(':') + 1) : current));
            initialExtra.put("stat", extra.get("stat"));
            choice("Stat Name", labels, () -> extra.get("stat"), v -> extra.put("stat", v)).tip("What is counted.");
        }
        if (task != null && task.type().equals("gamestage")) {

            extra.put("stage", p.getString("stage", ""));
            extra.put("team_stage", p.getBool("team_stage", false) ? "True" : "False");
            initialExtra.putAll(extra);
            text("Stage", () -> extra.get("stage"), v -> extra.put("stage", v)).tip("The stage the player has to have. Here it is a player tag (/scoreboard players tag <player> add <stage>).");
            choice("Team Stage", List.of("False", "True"), () -> extra.get("team_stage"), v -> extra.put("team_stage", v))
                    .tip("Kept for files from newer versions; this game checks the player's own tags.");
        }
        if (task != null && task.type().equals("interaction")) {

            extra.put("to_interact", p.getString("to_interact", "minecraft:air"));
            extra.put("interaction_type", p.getString("interaction_type", "block"));
            initialExtra.putAll(extra);
            text("Match", () -> extra.get("to_interact"), v -> extra.put("to_interact", v)).tip("A registry name such as minecraft:chest.");
            choice("Type", com.levviata.reflibquests.model.BookEditor.INTERACTION_TYPES, () -> extra.get("interaction_type"), v -> extra.put("interaction_type", v))
                    .tip("What is right-clicked. Tags and block states are not known in this game: a tag matches nothing, a state is read as its block.");
        }
        if (task != null && task.type().equals("advancement")) {
            extra.put("criterion", p.getString("criterion", ""));
            initialExtra.put("criterion", extra.get("criterion"));
            text("Criterion", () -> extra.get("criterion"), v -> extra.put("criterion", v)).tip("A single criterion of the advancement. Empty needs the whole advancement.");
        }
        if (task != null && task.type().equals("observation")) {
            extra.put("timer", String.valueOf(p.getInt("timer", 0)));
            extra.put("observation_type", p.getString("observation_type", "block"));
            initialExtra.put("timer", extra.get("timer"));
            initialExtra.put("observation_type", extra.get("observation_type"));
            text("Timer", () -> extra.get("timer"), v -> extra.put("timer", v)).tip("Ticks the thing has to be watched. This game counts one look.");
            choice("Type", List.of("block", "entity"), () -> extra.get("observation_type"), v -> extra.put("observation_type", v))
                    .tip("What is observed. Only blocks are detected in this game.");
        }
        if (task != null && task.type().equals("location")) {
            extra.put("dimension", p.getString("dimension", "minecraft:overworld"));
            extra.put("ignore_dimension", p.getBool("ignore_dimension", false) ? "True" : "False");
            initialExtra.put("dimension", extra.get("dimension"));
            initialExtra.put("ignore_dimension", extra.get("ignore_dimension"));
            text("Dimension", () -> extra.get("dimension"), v -> extra.put("dimension", v)).tip("The dimension the box is in.");
            choice("Ignore Dimension", List.of("False", "True"), () -> extra.get("ignore_dimension"), v -> extra.put("ignore_dimension", v))
                    .tip("The box counts in any dimension.");
        }
        if (task != null && task.type().equals("location")) {
            List<com.levviata.reflib.api.data.DataNode> pos = p.getList("position");
            List<com.levviata.reflib.api.data.DataNode> size = p.getList("size");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                com.levviata.reflib.api.data.DataNode n = (i < 3 ? pos : size).size() == 3 ? (i < 3 ? pos : size).get(i % 3) : null;
                b.append(i > 0 ? ", " : "").append(n instanceof DataNode.Int v ? v.value() : 0);
            }
            box = b.toString();
            initialBox = box;

            String[] names = {"X Position", "Y Position", "Z Position", "X Size (W)", "Y Size (H)", "Z Size (D)"};
            for (int k = 0; k < 6; k++) {
                final int at = k;
                number(names[k], true, () -> box.split(", *")[at], v -> {
                    String[] parts = box.split(", *");
                    long n;
                    try {
                        n = Long.parseLong(v.strip());
                    } catch (NumberFormatException ex) {
                        return;
                    }
                    parts[at] = String.valueOf(at >= 3 ? Math.max(1, n) : n);
                    box = String.join(", ", parts);
                }).tip(k < 3 ? "A corner of the box, in blocks." : "How many blocks the box measures.");
            }
        }
        if (task != null) {
            choice("Optional Task", List.of("False", "True"), () -> optional, v -> optional = v)
                    .tip("The quest can be completed without this task.");
            flag("Disable Completion Toast", "disable_toast", List.of("False", "True"), yesNo(p, "disable_toast"),
                    "No pop-up when this task is completed.");
            if (task.type().equals("item")) {
                flag("Only From Task Screen", "task_screen_only", List.of("False", "True"), yesNo(p, "task_screen_only"),
                        "Progress only counts when done through the task screen.");
            }
            if (task.type().equals("item")) {
                String crafting = !p.has("only_from_crafting") ? "Default" : p.getBool("only_from_crafting", false) ? "True" : "False";
                flag("Only Detect From Crafting", "only_from_crafting", CONSUME_LABELS, crafting, "Only items made by crafting count.");
                String match = p.getString("match_components", "default");
                flag("Match Data Components", "match_components", List.of("Default", "Fuzzy", "Exact"),
                        Character.toUpperCase(match.charAt(0)) + match.substring(1), "Default and Fuzzy ignore the item's data; Exact needs it to match.");
                choice("Consume Items", CONSUME_LABELS, () -> consume, v -> consume = v)
                        .tip("Default follows the book's setting. True takes the items when they are handed in.");
            }
        } else {
            String rt = reward.type();
            if (rt.equals("item") && p.get("item") instanceof DataNode.Compound rewardItem) {

                String rewardNbt = rewardItem.getCompound("nbt").entries().isEmpty() ? "" : "|" + com.levviata.reflib.api.data.Snbt.printCompact(rewardItem.getCompound("nbt"));
                extra.put("item", rewardItem.getString("id", "") + (rewardItem.getInt("damage", 0) > 0 || !rewardNbt.isEmpty() ? "|" + rewardItem.getInt("damage", 0) : "") + rewardNbt);
                Entry rewardItemRow = text("Item", () -> extra.get("item"), v -> extra.put("item", v)).tip("The item that is given.");
                rewardItemRow.plain = true;
                rewardItemRow.pick = done -> net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        SelectorScreen.items(net.minecraft.client.Minecraft.getMinecraft().currentScreen, stack ->
                                done.accept(stack.getItem().getRegistryName() + (stack.getMetadata() > 0 || stack.hasTagCompound() ? "|" + stack.getMetadata() : "")
                                        + (stack.hasTagCompound() && !stack.getTagCompound().isEmpty() ? "|" + stack.getTagCompound() : ""))
                        ).preselectItem(stackOfRow(extra.get("item"))));
            }
            if (rt.equals("item")) {
                extra.put("random_bonus", String.valueOf(Math.max(0, p.getInt("random_bonus", 0))));
                text("Random Bonus", () -> extra.get("random_bonus"), v -> extra.put("random_bonus", v))
                        .tip("Up to this many more items are given at random on top of the count.");
            }
            if (rt.equals("command")) {
                extra.put("feedback_message", p.getString("feedback_message", ""));
                text("Feedback Toast", () -> extra.get("feedback_message"), v -> extra.put("feedback_message", v))
                        .tip("Text shown to the player when the command has run.");
                extra.put("command", p.getString("command", ""));
                extra.put("permission_level", String.valueOf(Math.max(0, Math.min(4, p.getInt("permission_level", 0)))));
                extra.put("silent", p.getBool("silent", false) ? "True" : "False");
                text("Command", () -> extra.get("command"), v -> extra.put("command", v)).tip("The command run for the player. @p is the player.");
                choice("Run with Permission Level", List.of("0", "1", "2", "3", "4"), () -> extra.get("permission_level"), v -> extra.put("permission_level", v))
                        .tip("0 default, 1, 2 Game Master, 3 Server Admin, 4 Server Owner.");
                choice("Silent", List.of("False", "True"), () -> extra.get("silent"), v -> extra.put("silent", v));
            } else if (rt.equals("gamestage")) {

                extra.put("remove", p.getBool("remove", false) ? "True" : "False");
                extra.put("stage", p.getString("stage", ""));
                choice("Remove", List.of("False", "True"), () -> extra.get("remove"), v -> extra.put("remove", v))
                        .tip("Take the stage away instead of giving it.");
                text("Stage", () -> extra.get("stage"), v -> extra.put("stage", v))
                        .tip("Here a stage is a player tag (the Stage task looks for the same tag).");
            } else if (rt.equals("toast")) {
                extra.put("description", p.getString("description", ""));
                text("Description", () -> extra.get("description"), v -> extra.put("description", v));
            } else if (rt.equals("advancement")) {
                extra.put("advancement", p.getString("advancement", ""));
                extra.put("criterion", p.getString("criterion", ""));
                text("Advancement", () -> extra.get("advancement"), v -> extra.put("advancement", v)).tip("A registry name such as minecraft:story/mine_stone.");
                text("Criterion", () -> extra.get("criterion"), v -> extra.put("criterion", v));
            }
            initialExtra.putAll(extra);
            choice("Auto-claim", AUTO_LABELS, () -> auto, v -> auto = v)
                    .tip("Whether the reward is given by itself once the quest is complete.");
            choice("Per-Team Reward", CONSUME_LABELS, () -> team, v -> team = v)
                    .tip("When true, this is one reward for the whole team; only the submitting player receives it. When false, all online players receive the reward");
            Entry excludeRow = choice("Exclude from Claim All", List.of("False", "True"), () -> exclude, v -> exclude = v);
            if (com.levviata.reflibquests.model.BookEditor.TABLE_REWARD_TYPES.contains(rt)) {

                exclude = "True";
                initialExclude = "True";
                excludeRow.enabled = () -> false;
                com.levviata.reflibquests.model.QuestBook tableBook = ClientQuests.book();
                com.levviata.reflibquests.model.Definitions.RewardTable table = tableBook == null ? null : tableBook.tables().get(com.levviata.reflibquests.io.Ids.tableId(p));
                final String tableName = table == null ? com.levviata.reflibquests.io.Ids.tableId(p) : table.title.isEmpty() ? table.id : table.title;
                Entry tableRow = text("Reward Table", () -> tableName, v -> { });
                tableRow.enabled = () -> false;
            }
            choice("Ignore reward blocking", List.of("False", "True"), () -> ignoreBlocking, v -> ignoreBlocking = v);
            flag("Disable Reward Screen Blur", "disable_reward_screen_blur", List.of("False", "True"), yesNo(p, "disable_reward_screen_blur"),
                    "The reward screen does not blur what is behind it.");
            if (rt.equals("item")) {
                flag("Only One", "only_one", List.of("False", "True"), yesNo(p, "only_one"), "If inventory has that item, doesn't give it. Ignores NBT.");
            }
        }
    }

    private static net.minecraft.item.ItemStack stackOfRow(String row) {
        if (row == null || row.isBlank()) {
            return net.minecraft.item.ItemStack.EMPTY;
        }
        String[] parts = row.split("[|]", 3);
        net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(parts[0]);
        if (item == null) {
            return net.minecraft.item.ItemStack.EMPTY;
        }
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item, 1, (int) parseLong(parts.length > 1 ? parts[1] : "0", 0));
        if (parts.length > 2 && !parts[2].isBlank()) {
            try {
                stack.setTagCompound(net.minecraft.nbt.JsonToNBT.getTagFromJson(parts[2]));
            } catch (net.minecraft.nbt.NBTException ex) {

            }
        }
        return stack;
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s == null ? "" : s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void flag(String label, String key, List<String> options, String current, String tip) {
        flags.put(key, current);
        initialFlags.put(key, current);
        choice(label, options, () -> flags.get(key), v -> flags.put(key, v)).tip(tip);
    }

    private static String yesNo(com.levviata.reflib.api.data.DataNode.Compound p, String key) {
        return p.getBool(key, false) ? "True" : "False";
    }

    private static String statLabel(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private boolean rowsOrdered;

    @Override
    public void initGui() {
        super.initGui();
        if (task == null && !rowsOrdered) {
            rowsOrdered = true;

            int start = 0;
            while (start < entries.size() && !entries.get(start).label.equals("Tags")) {
                start++;
            }
            start++;
            if (start < entries.size()) {
                List<Entry> rest = new java.util.ArrayList<>(entries.subList(start, entries.size()));
                rest.sort(java.util.Comparator.comparing(e -> e.label.toLowerCase(java.util.Locale.ROOT)));
                for (int i = 0; i < rest.size(); i++) {
                    entries.set(start + i, rest.get(i));
                }
            }
        }
        if (task != null && !rowsOrdered) {
            rowsOrdered = true;

            int from = 0;
            while (from < entries.size() && !entries.get(from).label.equals("Tags")) {
                from++;
            }
            from++;
            if (from < entries.size()) {
                List<Entry> middle = new java.util.ArrayList<>(entries.subList(from, entries.size()));
                Entry toast = null;
                for (Entry e : new java.util.ArrayList<>(middle)) {
                    if (e.label.equals("Disable Completion Toast")) {
                        toast = e;
                        middle.remove(e);
                    }
                }

                middle.sort(java.util.Comparator.comparing(e -> e.label.endsWith(" Position") ? "~1" + e.label
                        : e.label.endsWith(")") && e.label.contains(" Size (") ? "~2" + e.label : e.label.toLowerCase(java.util.Locale.ROOT)));
                if (toast != null) {
                    middle.add(toast);
                }
                for (int i = 0; i < middle.size(); i++) {
                    entries.set(from + i, middle.get(i));
                }
            }
        }
    }

    private void change(String op, String id, String key, String value) {
        emit(op, DataNode.Compound.empty().with("id", id).with("key", key).with("value", value));
    }

    @Override
    protected void accepted() {
        applyEdits();
        if (rewardDraft != null) {
            TableEdits.record("add_table_entry", DataNode.Compound.empty().with("id", rewardDraft.tableId()).with("weight", 1.0)
                    .with("entry", rewardDraft.entryId()).with("type", rewardDraft.type()).with("params", rewardDraft.params()));
            for (DataNode.Compound e : draftEdits) {
                String op = e.getString("op", "");
                if (op.equals("set_reward_prop")) {
                    TableEdits.record(op, e.getCompound("args"));
                } else {
                    TableEdits.send(op, e.getCompound("args"));
                }
            }
        }
        if (draft != null) {

            String questTitle = null;
            for (DataNode.Compound e : draftEdits) {
                if (e.getString("op", "").equals("set_task_prop") && e.getCompound("args").getString("key", "").equals("title")) {
                    questTitle = e.getCompound("args").getString("value", "");
                }
            }
            if (questTitle == null || questTitle.isBlank()) {
                questTitle = com.levviata.reflibquests.text.RichText.stripCodes(QuestText.taskTitle(task));
            }
            DataNode.Compound create = DataNode.Compound.empty().with("chapter", draft.chapter()).with("x", draft.x()).with("y", draft.y())
                    .with("title", questTitle).with("task", draft.taskArgs()).with("edits", new DataNode.Lst(new java.util.ArrayList<DataNode>(draftEdits)));
            TableEdits.send("create_quest", create);
        }
    }

    private void applyEdits() {
        sendFlags();
        String kindName = task != null ? "task" : "reward";
        String ownId = task != null ? task.id() : reward.id();
        DataNode.Compound iconEdit = IconPicker.edit(kindName, ownId, iconChoice);
        if (iconEdit != null) {
            emit("set_icon", iconEdit);
        }
        if (!tags.equals(initialTags)) {
            change(task != null ? "set_task_prop" : "set_reward_prop", ownId, "tags", tags);
        }
        if (task != null) {
            if (!title.equals(initialTitle)) {
                change("set_task_prop", task.id(), "title", title.strip());
            }
            if (!amount.isEmpty() && !amount.equals(initialAmount)) {
                emit("task_value", DataNode.Compound.empty().with("id", task.id()).with("value", Long.parseLong(amount)));
            }
            if (!box.equals(initialBox)) {
                change("set_task_prop", task.id(), "box", box.strip());
            }
            if (!target.equals(initialTarget)) {
                change("set_task_prop", task.id(), "target", target.strip());
            }
            for (java.util.Map.Entry<String, String> e : extra.entrySet()) {
                if (!e.getValue().equals(initialExtra.get(e.getKey()))) {
                    String ev = e.getValue().strip();
                    if (e.getKey().equals("stat")) {
                        for (String id : com.levviata.reflibquests.model.BookEditor.STATS) {
                            if (statLabel(id).equals(ev)) {
                                ev = "minecraft:" + id;
                            }
                        }
                    }
                    change("set_task_prop", task.id(), e.getKey(), e.getKey().equals("ignore_dimension") || e.getKey().equals("team_stage") ? ev.toLowerCase(java.util.Locale.ROOT) : ev);
                }
            }
            if (!optional.equals(initialOptional)) {
                change("set_task_prop", task.id(), "optional_task", optional.equals("True") ? "true" : "false");
            }
            if (!consume.equals(initialConsume)) {
                change("set_task_prop", task.id(), "consume_items", consume.toLowerCase(java.util.Locale.ROOT));
            }
        } else {
            if (!title.equals(initialTitle)) {
                change("set_reward_prop", reward.id(), "title", title.strip());
            }
            if (!amount.isEmpty() && !amount.equals(initialAmount)) {
                String key = reward.type().equals("item") ? "count" : reward.type();
                change("set_reward_prop", reward.id(), key, amount);
            }
            for (java.util.Map.Entry<String, String> e : extra.entrySet()) {
                if (!e.getValue().equals(initialExtra.get(e.getKey()))) {
                    String v = e.getKey().equals("silent") || e.getKey().equals("remove") ? e.getValue().toLowerCase(java.util.Locale.ROOT) : e.getValue().strip();
                    change("set_reward_prop", reward.id(), e.getKey(), v);
                }
            }
            if (!team.equals(initialTeam)) {
                change("set_reward_prop", reward.id(), "team_reward", team.toLowerCase(java.util.Locale.ROOT));
            }
            if (!exclude.equals(initialExclude)) {
                change("set_reward_prop", reward.id(), "exclude_from_claim_all", exclude.toLowerCase(java.util.Locale.ROOT));
            }
            if (!ignoreBlocking.equals(initialIgnore)) {
                change("set_reward_prop", reward.id(), "ignore_reward_blocking", ignoreBlocking.toLowerCase(java.util.Locale.ROOT));
            }
            if (!auto.equals(initialAuto)) {
                change("set_reward_prop", reward.id(), "auto", AUTO_VALUES.get(AUTO_LABELS.indexOf(auto)));
            }
        }
    }

    private void sendFlags() {
        for (java.util.Map.Entry<String, String> e : flags.entrySet()) {
            if (!e.getValue().equals(initialFlags.get(e.getKey()))) {
                change(task != null ? "set_task_prop" : "set_reward_prop", task != null ? task.id() : reward.id(), e.getKey(),
                        e.getValue().toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    @Override
    protected void cancelled() {

    }
}

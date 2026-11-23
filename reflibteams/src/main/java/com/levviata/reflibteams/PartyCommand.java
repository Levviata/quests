/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibteams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import com.levviata.reflib.api.team.Teams;
import com.levviata.reflibteams.PartyManager.Party;
import com.levviata.reflibteams.PartyManager.Result;

public final class PartyCommand extends CommandBase {

    private static final List<String> SUBS = List.of("create", "invite", "join", "leave", "kick", "disband", "info", "list", "invites");

    @Override
    public String getName() {
        return "rteam";
    }

    @Override
    public List<String> getAliases() {
        return List.of("party");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rteam <create <name> | invite <player> | join <party> | leave | kick <player> | disband | info | list | invites>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBS);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return List.of();
    }

    private static void say(ICommandSender to, String text) {
        to.sendMessage(new TextComponentString("§6[Party] §r" + text));
    }

    private static void sayTo(MinecraftServer server, UUID player, String text) {
        EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(player);
        if (p != null) {
            say(p, text);
        }
    }

    private static void tellParty(MinecraftServer server, Party party, UUID except, String text) {
        for (UUID m : party.members) {
            if (!m.equals(except)) {
                sayTo(server, m, text);
            }
        }
    }

    private static String describe(Result r) {
        switch (r) {
            case ALREADY_IN_PARTY:
                return "You are already in a party. Leave it first with /rteam leave.";
            case NOT_IN_PARTY:
                return "You are not in a party.";
            case BAD_NAME:
                return "A party name needs " + PartyManager.MIN_NAME + "-" + PartyManager.MAX_NAME + " letters, digits, spaces, - _ or '.";
            case NAME_TAKEN:
                return "That party name is taken.";
            case NOT_OWNER:
                return "Only the party owner can do that.";
            case TARGET_NOT_MEMBER:
                return "That player is not in your party.";
            case TARGET_IN_PARTY:
                return "That player is already in a party.";
            case FULL:
                return "The party is full.";
            case NO_INVITE:
                return "You have no valid invite from that party (invites last 5 minutes).";
            case UNKNOWN_PARTY:
                return "There is no party with that name.";
            case SELF:
                return "You cannot do that to yourself.";
            default:
                return "Done.";
        }
    }

    private static String nameOf(MinecraftServer server, PartyManager m, UUID id) {
        EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
        return p != null ? p.getName() : m.nameOf(id);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP self = getCommandSenderAsPlayer(sender);
        UUID me = self.getUniqueID();
        PartyManager m = PartyTeamProvider.manager;
        m.rememberName(me, self.getName());
        long now = System.currentTimeMillis();
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create": {
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
                Result r = m.create(me, name, now);
                say(sender, r == Result.OK ? "Party §e" + name + "§r created. Invite players with /rteam invite <player>." : describe(r));
                break;
            }
            case "invite": {
                EntityPlayerMP target = getPlayer(server, sender, arg(args, 1));
                m.rememberName(target.getUniqueID(), target.getName());
                Result r = m.invite(me, target.getUniqueID(), now);
                if (r == Result.OK) {
                    String party = m.partyOf(me).orElseThrow().name;
                    say(sender, "Invited " + target.getName() + ".");
                    say(target, self.getName() + " invited you to §e" + party + "§r. Accept with /rteam join " + party);
                } else {
                    say(sender, describe(r));
                }
                break;
            }
            case "join": {
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
                Result r = m.join(me, name, now);
                if (r == Result.OK) {
                    Party p = m.partyOf(me).orElseThrow();
                    say(sender, "You joined §e" + p.name + "§r. Your quest progress is now shared with the party.");
                    tellParty(server, p, me, self.getName() + " joined the party.");
                } else {
                    say(sender, describe(r));
                }
                break;
            }
            case "leave": {
                Optional<Party> before = m.partyOf(me);
                Result r = m.leave(me);
                if (r == Result.OK) {
                    say(sender, "You left the party. You are back on your own progress.");
                    before.ifPresent(p -> tellParty(server, p, me, self.getName() + " left the party."));
                } else {
                    say(sender, describe(r));
                }
                break;
            }
            case "kick": {
                String targetName = arg(args, 1);
                Optional<UUID> target = Optional.ofNullable(server.getPlayerList().getPlayerByUsername(targetName))
                        .map(EntityPlayerMP::getUniqueID).or(() -> m.playerByName(targetName));
                if (target.isEmpty()) {
                    say(sender, "Unknown player " + targetName + ".");
                    break;
                }
                Optional<Party> before = m.partyOf(me);
                Result r = m.kick(me, target.get());
                if (r == Result.OK) {
                    say(sender, "Removed " + targetName + " from the party.");
                    sayTo(server, target.get(), "You were removed from the party.");
                    before.ifPresent(p -> tellParty(server, p, me, targetName + " was removed from the party."));
                } else {
                    say(sender, describe(r));
                }
                break;
            }
            case "disband": {
                Optional<Party> before = m.partyOf(me);
                List<UUID> members = before.map(p -> List.copyOf(p.members)).orElse(List.of());
                Result r = m.disband(me);
                if (r == Result.OK) {
                    for (UUID member : members) {
                        sayTo(server, member, "The party was disbanded.");
                    }
                } else {
                    say(sender, describe(r));
                }
                break;
            }
            case "info": {
                Optional<Party> p = m.partyOf(me);
                if (p.isEmpty()) {
                    say(sender, "You are not in a party. Create one with /rteam create <name>.");
                    break;
                }
                say(sender, "§e" + p.get().name + "§r, " + p.get().members.size() + "/" + m.maxMembers() + " members:");
                for (UUID member : p.get().members) {
                    boolean online = server.getPlayerList().getPlayerByUUID(member) != null;
                    say(sender, " - " + (member.equals(p.get().owner) ? "§6[owner] §r" : "") + nameOf(server, m, member)
                            + (online ? " §a(online)" : " §8(offline)"));
                }
                break;
            }
            case "list": {
                if (m.parties().isEmpty()) {
                    say(sender, "There are no parties.");
                }
                for (Party p : m.parties()) {
                    say(sender, p.name + " (" + p.members.size() + " members, owner " + nameOf(server, m, p.owner) + ")");
                }
                break;
            }
            case "invites": {
                List<Party> invites = m.invitesFor(me, now);
                say(sender, invites.isEmpty() ? "You have no invites." : "You are invited to:");
                for (Party p : invites) {
                    say(sender, " - " + p.name + " (/rteam join " + p.name + ")");
                }
                break;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
        for (UUID changed : m.drainAffected()) {
            Teams.notifyChanged(changed);
        }
    }

    private static String arg(String[] args, int i) throws WrongUsageException {
        if (args.length <= i) {
            throw new WrongUsageException("Missing argument");
        }
        return args[i];
    }
}

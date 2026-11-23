/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibteams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.levviata.reflib.api.data.DataNode;

public final class PartyManager {

    public static final int MIN_NAME = 3;
    public static final int MAX_NAME = 24;
    public static final long INVITE_VALID_MILLIS = 5 * 60 * 1000L;

    public enum Result {
        OK, ALREADY_IN_PARTY, NOT_IN_PARTY, BAD_NAME, NAME_TAKEN, NOT_OWNER, TARGET_NOT_MEMBER, TARGET_IN_PARTY,
        FULL, NO_INVITE, UNKNOWN_PARTY, SELF
    }

    public static final class Party {
        public final UUID id;
        public String name;
        public UUID owner;
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        Party(UUID id, String name, UUID owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.members.add(owner);
        }
    }

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, UUID> partyOfPlayer = new HashMap<>();

    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<UUID> affected = new ArrayList<>();
    private int maxMembers = 8;
    private boolean dirty;

    public int maxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int max) {
        this.maxMembers = Math.max(2, max);
    }

    public Optional<Party> partyOf(UUID player) {
        UUID id = partyOfPlayer.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(parties.get(id));
    }

    public UUID teamOf(UUID player) {
        UUID id = partyOfPlayer.get(player);
        return id == null ? player : id;
    }

    public Set<UUID> membersOf(UUID team) {
        Party p = parties.get(team);
        return p == null ? Set.of(team) : Set.copyOf(p.members);
    }

    public List<Party> parties() {
        return new ArrayList<>(parties.values());
    }

    public Optional<Party> byName(String name) {
        for (Party p : parties.values()) {
            if (p.name.equalsIgnoreCase(name)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public List<Party> invitesFor(UUID player, long now) {
        List<Party> out = new ArrayList<>();
        Map<UUID, Long> m = invites.get(player);
        if (m != null) {
            m.entrySet().removeIf(e -> e.getValue() < now || !parties.containsKey(e.getKey()));
            for (UUID id : m.keySet()) {
                out.add(parties.get(id));
            }
        }
        return out;
    }

    public void rememberName(UUID player, String name) {
        if (!name.equals(names.put(player, name))) {
            dirty = true;
        }
    }

    public String nameOf(UUID player) {
        return names.getOrDefault(player, player.toString().substring(0, 8));
    }

    public Optional<UUID> playerByName(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public List<UUID> drainAffected() {
        List<UUID> out = new ArrayList<>(new LinkedHashSet<>(affected));
        affected.clear();
        return out;
    }

    private static boolean validName(String name) {
        return name != null && name.length() >= MIN_NAME && name.length() <= MAX_NAME && name.matches("[\\p{L}\\p{N} _'-]+")
                && !name.isBlank();
    }

    public Result create(UUID owner, String name, long now) {
        if (partyOfPlayer.containsKey(owner)) {
            return Result.ALREADY_IN_PARTY;
        }
        if (!validName(name)) {
            return Result.BAD_NAME;
        }
        if (byName(name).isPresent()) {
            return Result.NAME_TAKEN;
        }
        Party p = new Party(UUID.randomUUID(), name.trim(), owner);
        parties.put(p.id, p);
        partyOfPlayer.put(owner, p.id);
        invites.remove(owner);
        affected.add(owner);
        dirty = true;
        return Result.OK;
    }

    public Result invite(UUID inviter, UUID target, long now) {
        Optional<Party> party = partyOf(inviter);
        if (party.isEmpty()) {
            return Result.NOT_IN_PARTY;
        }
        if (!party.get().owner.equals(inviter)) {
            return Result.NOT_OWNER;
        }
        if (inviter.equals(target)) {
            return Result.SELF;
        }
        if (partyOfPlayer.containsKey(target)) {
            return Result.TARGET_IN_PARTY;
        }
        if (party.get().members.size() >= maxMembers) {
            return Result.FULL;
        }
        invites.computeIfAbsent(target, t -> new HashMap<>()).put(party.get().id, now + INVITE_VALID_MILLIS);
        return Result.OK;
    }

    public Result join(UUID player, String partyName, long now) {
        if (partyOfPlayer.containsKey(player)) {
            return Result.ALREADY_IN_PARTY;
        }
        Optional<Party> party = byName(partyName);
        if (party.isEmpty()) {
            return Result.UNKNOWN_PARTY;
        }
        Map<UUID, Long> mine = invites.get(player);
        Long expiry = mine == null ? null : mine.get(party.get().id);
        if (expiry == null || expiry < now) {
            return Result.NO_INVITE;
        }
        if (party.get().members.size() >= maxMembers) {
            return Result.FULL;
        }
        party.get().members.add(player);
        partyOfPlayer.put(player, party.get().id);
        invites.remove(player);
        affected.add(player);
        dirty = true;
        return Result.OK;
    }

    public Result leave(UUID player) {
        Optional<Party> party = partyOf(player);
        if (party.isEmpty()) {
            return Result.NOT_IN_PARTY;
        }
        remove(party.get(), player);
        return Result.OK;
    }

    public Result kick(UUID actor, UUID target) {
        Optional<Party> party = partyOf(actor);
        if (party.isEmpty()) {
            return Result.NOT_IN_PARTY;
        }
        if (!party.get().owner.equals(actor)) {
            return Result.NOT_OWNER;
        }
        if (actor.equals(target)) {
            return Result.SELF;
        }
        if (!party.get().members.contains(target)) {
            return Result.TARGET_NOT_MEMBER;
        }
        remove(party.get(), target);
        return Result.OK;
    }

    public Result disband(UUID actor) {
        Optional<Party> party = partyOf(actor);
        if (party.isEmpty()) {
            return Result.NOT_IN_PARTY;
        }
        if (!party.get().owner.equals(actor)) {
            return Result.NOT_OWNER;
        }
        for (UUID m : new ArrayList<>(party.get().members)) {
            partyOfPlayer.remove(m);
            affected.add(m);
        }
        parties.remove(party.get().id);
        dropInvitesFor(party.get().id);
        dirty = true;
        return Result.OK;
    }

    private void remove(Party party, UUID player) {
        party.members.remove(player);
        partyOfPlayer.remove(player);
        affected.add(player);
        dirty = true;
        if (party.members.isEmpty()) {
            parties.remove(party.id);
            dropInvitesFor(party.id);
        } else if (party.owner.equals(player)) {
            party.owner = party.members.iterator().next();
        }
    }

    private void dropInvitesFor(UUID partyId) {
        for (Map<UUID, Long> m : invites.values()) {
            m.remove(partyId);
        }
    }

    public DataNode.Compound toData() {
        List<DataNode> list = new ArrayList<>();
        for (Party p : parties.values()) {
            List<DataNode> members = new ArrayList<>();
            for (UUID m : p.members) {
                members.add(new DataNode.Str(m.toString()));
            }
            list.add(DataNode.Compound.empty().with("id", p.id.toString()).with("name", p.name)
                    .with("owner", p.owner.toString()).with("members", new DataNode.Lst(members)));
        }
        DataNode.Compound nameMap = DataNode.Compound.empty();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            nameMap = nameMap.with(e.getKey().toString(), e.getValue());
        }
        return DataNode.Compound.empty().with("dataVersion", 1).with("parties", new DataNode.Lst(list)).with("names", nameMap);
    }

    public static PartyManager fromData(DataNode.Compound c) {
        if (c.getInt("dataVersion", 1) > 1) {
            throw new IllegalArgumentException("party data version " + c.getInt("dataVersion", 1) + " is newer than supported 1");
        }
        PartyManager m = new PartyManager();
        for (DataNode n : c.getList("parties")) {
            if (!(n instanceof DataNode.Compound pc)) {
                continue;
            }
            try {
                UUID id = UUID.fromString(pc.getString("id", ""));
                UUID owner = UUID.fromString(pc.getString("owner", ""));
                Party p = new Party(id, pc.getString("name", "Party"), owner);
                for (DataNode mn : pc.getList("members")) {
                    if (mn instanceof DataNode.Str s) {
                        UUID member = UUID.fromString(s.value());
                        if (!m.partyOfPlayer.containsKey(member)) {
                            p.members.add(member);
                        }
                    }
                }
                if (m.partyOfPlayer.containsKey(owner)) {
                    continue;
                }
                for (UUID member : p.members) {
                    m.partyOfPlayer.put(member, id);
                }
                m.parties.put(id, p);
            } catch (IllegalArgumentException ex) {

            }
        }
        for (var e : c.getCompound("names").entries().entrySet()) {
            if (e.getValue() instanceof DataNode.Str s) {
                try {
                    m.names.put(UUID.fromString(e.getKey()), s.value());
                } catch (IllegalArgumentException ex) {

                }
            }
        }
        return m;
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

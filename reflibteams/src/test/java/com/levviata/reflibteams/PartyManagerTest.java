/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibteams;

import static com.levviata.reflibteams.PartyManager.Result.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.Snbt;

class PartyManagerTest {

    private PartyManager m;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private static final long T0 = 1_000_000L;

    @BeforeEach
    void setUp() {
        m = new PartyManager();
    }

    @Test
    void soloPlayerIsTheirOwnTeam() {
        assertEquals(a, m.teamOf(a));
        assertEquals(Set.of(a), m.membersOf(a));
        assertTrue(m.partyOf(a).isEmpty());
    }

    @Test
    void createMakesAPartyWithTheOwner() {
        assertEquals(OK, m.create(a, "Red Team", T0));
        var party = m.partyOf(a).orElseThrow();
        assertEquals("Red Team", party.name);
        assertEquals(party.id, m.teamOf(a));
        assertEquals(Set.of(a), m.membersOf(party.id));
        assertEquals(List.of(a), m.drainAffected());
        assertTrue(m.drainAffected().isEmpty());
    }

    @Test
    void createRules() {
        assertEquals(BAD_NAME, m.create(a, "ab", T0));
        assertEquals(BAD_NAME, m.create(a, "x".repeat(25), T0));
        assertEquals(BAD_NAME, m.create(a, "no/slashes", T0));
        assertEquals(OK, m.create(a, "Team", T0));
        assertEquals(ALREADY_IN_PARTY, m.create(a, "Another", T0));
        assertEquals(NAME_TAKEN, m.create(b, "TEAM", T0));
    }

    @Test
    void inviteAndJoinFlow() {
        m.create(a, "Team", T0);
        assertEquals(NO_INVITE, m.join(b, "Team", T0));
        assertEquals(OK, m.invite(a, b, T0));
        assertEquals(1, m.invitesFor(b, T0).size());
        assertEquals(OK, m.join(b, "team", T0 + 1000));
        UUID id = m.partyOf(a).orElseThrow().id;
        assertEquals(id, m.teamOf(b));
        assertEquals(Set.of(a, b), m.membersOf(id));
        assertTrue(m.invitesFor(b, T0).isEmpty(), "invite is used up");
        assertEquals(ALREADY_IN_PARTY, m.join(b, "Team", T0));
    }

    @Test
    void inviteRules() {
        assertEquals(NOT_IN_PARTY, m.invite(a, b, T0));
        m.create(a, "Team", T0);
        m.create(c, "Other", T0);
        assertEquals(SELF, m.invite(a, a, T0));
        assertEquals(TARGET_IN_PARTY, m.invite(a, c, T0));
        m.invite(a, b, T0);
        m.join(b, "Team", T0);
        assertEquals(NOT_OWNER, m.invite(b, UUID.randomUUID(), T0));
    }

    @Test
    void invitesExpireAndAreForOnePartyOnly() {
        m.create(a, "Team", T0);
        m.create(c, "Other", T0);
        m.invite(a, b, T0);
        assertEquals(NO_INVITE, m.join(b, "Other", T0), "an invite from Team does not work for Other");
        assertEquals(NO_INVITE, m.join(b, "Team", T0 + PartyManager.INVITE_VALID_MILLIS + 1));
        assertTrue(m.invitesFor(b, T0 + PartyManager.INVITE_VALID_MILLIS + 1).isEmpty());
        assertEquals(UNKNOWN_PARTY, m.join(b, "Nope", T0));
    }

    @Test
    void partySizeLimit() {
        m.setMaxMembers(2);
        m.create(a, "Team", T0);
        m.invite(a, b, T0);
        m.join(b, "Team", T0);
        assertEquals(FULL, m.invite(a, c, T0));
        m.setMaxMembers(3);
        m.invite(a, c, T0);
        m.setMaxMembers(2);
        assertEquals(FULL, m.join(c, "Team", T0), "limit is checked again when joining");
    }

    @Test
    void leaveHandsOwnershipOverAndLastMemberRemovesTheParty() {
        m.create(a, "Team", T0);
        m.invite(a, b, T0);
        m.join(b, "Team", T0);
        m.invite(a, c, T0);
        m.join(c, "Team", T0);
        m.drainAffected();
        assertEquals(OK, m.leave(a));
        assertEquals(b, m.partyOf(b).orElseThrow().owner, "the longest standing member becomes owner");
        assertEquals(a, m.teamOf(a), "back on the solo team");
        assertEquals(List.of(a), m.drainAffected());
        m.leave(b);
        m.leave(c);
        assertTrue(m.parties().isEmpty());
        assertEquals(NOT_IN_PARTY, m.leave(c));
    }

    @Test
    void kickRules() {
        m.create(a, "Team", T0);
        m.invite(a, b, T0);
        m.join(b, "Team", T0);
        assertEquals(NOT_OWNER, m.kick(b, a));
        assertEquals(SELF, m.kick(a, a));
        assertEquals(TARGET_NOT_MEMBER, m.kick(a, c));
        m.drainAffected();
        assertEquals(OK, m.kick(a, b));
        assertEquals(b, m.teamOf(b));
        assertEquals(List.of(b), m.drainAffected());
        assertEquals(NOT_IN_PARTY, m.kick(b, a));
    }

    @Test
    void disbandReleasesEveryone() {
        m.create(a, "Team", T0);
        m.invite(a, b, T0);
        m.join(b, "Team", T0);
        m.invite(a, c, T0);
        m.drainAffected();
        assertEquals(NOT_OWNER, m.disband(b));
        assertEquals(OK, m.disband(a));
        assertTrue(m.parties().isEmpty());
        assertEquals(b, m.teamOf(b));
        assertEquals(Set.of(a, b), Set.copyOf(m.drainAffected()));
        assertEquals(UNKNOWN_PARTY, m.join(c, "Team", T0), "a disbanded party is gone, and so are its invites");
        m.create(a, "Team", T0);
        assertEquals(NO_INVITE, m.join(c, "Team", T0), "a new party with the old name does not inherit old invites");
    }

    @Test
    void persistenceRoundTripThroughText() {
        m.create(a, "Team One", T0);
        m.invite(a, b, T0);
        m.join(b, "Team One", T0);
        m.rememberName(a, "Alice");
        m.rememberName(b, "Bob");
        PartyManager back = PartyManager.fromData(Snbt.parse(Snbt.print(m.toData())));
        assertEquals(m.partyOf(a).orElseThrow().id, back.teamOf(b));
        assertEquals("Team One", back.partyOf(a).orElseThrow().name);
        assertEquals(a, back.partyOf(b).orElseThrow().owner);
        assertEquals(b, back.playerByName("bob").orElseThrow());
        assertEquals("Alice", back.nameOf(a));
    }

    @Test
    void damagedEntriesAreSkippedNotFatal() {
        var data = Snbt.parse("""
                { dataVersion: 1, parties: [ { id: "garbage", name: "Bad", owner: "x", members: [] } ], names: { "not-a-uuid": "x" } }
                """);
        PartyManager back = PartyManager.fromData(data);
        assertTrue(back.parties().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PartyManager.fromData(Snbt.parse("{ dataVersion: 9 }")));
    }

    @Test
    void dirtyFlagTracksChanges() {
        assertFalse(m.isDirty());
        m.create(a, "Team", T0);
        assertTrue(m.isDirty());
        m.clearDirty();
        m.invite(a, b, T0);
        assertFalse(m.isDirty(), "invites are temporary and not saved");
        m.rememberName(a, "Alice");
        assertTrue(m.isDirty());
    }
}

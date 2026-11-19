# Reflib Teams

Party teams for mods built on reflib (such as Reflib Quests). Server side only: clients do not need this mod. LGPL-2.1-only. Independent implementation; not related to any other team mod.

## Use

`/rteam` (alias `/party`), open to all players:

| Command | Effect |
|---|---|
| `create <name>` | Start a party you own (3-24 letters, digits, spaces, `-`, `_`, `'`) |
| `invite <player>` | Owner invites an online player; invites last 5 minutes |
| `join <party>` | Accept an invite |
| `leave` | Leave your party (ownership passes to the longest standing member; the party disappears when empty) |
| `kick <player>` | Owner removes a member |
| `disband` | Owner dissolves the party |
| `info`, `list`, `invites` | Look things up |

While you are in a party, mods that ask reflib for your team (`Teams.get().teamOf(player)`) get the party, so progress is shared. Leaving returns you to your own solo progress, which was never changed. Parties are saved in the world (`data/reflibteams/parties.snbt`). The size limit is `maxMembers` in `config/reflibteams.cfg` (default 8).

## Status

| Part | State |
|---|---|
| Party rules (`PartyManager`): create, invite, join, leave, kick, disband, ownership hand-over, size limit, expiry, saving | Done, unit-tested (13 tests) |
| Commands, saving in the world, team change notification to other mods | Done. **Verified in game** with a dedicated server and two clients (2026-11-19): a party was created and joined, the party's progress was saved under the party's own team file, and the quest mod used it |
| Not done | Party chat, team names shown in the quest book, ownership transfer on request, allies, server-wide (admin) teams, an admin way to edit parties, rejoin-after-restart invite persistence (invites are temporary by design) |

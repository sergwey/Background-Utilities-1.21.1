# Background Utilities

A NeoForge mod for roleplay servers: a witness-based action log, three chat channels, chat and
display-name profiles written in markup, an operator item editor, a banner line that stays beside the
log, and a pair of tools for placing and removing visual effects.

## What it adds

- **Action log.** `/me` and `*asterisk*` lines — `^caret^` for silent ones — are recorded with every
  player who was within the witness radius, and shown to them in an on-screen log. Staff review,
  hide or delete entries from the administrator menu, and each action raises an alert for operators.
  A player silenced in actions gets nothing through at all, and on a server whose action recording is
  switched off an action is not said either: either way the actor is told why, and told which of the
  two it was.
- **Items in chat.** `[item]`, `[i]`, `[hand]`, `[offhand]`, `[head]`, `[chest]`, `[legs]` and `[feet]` in a
  message are taken out of the sentence — which is left with one space between its parts — and what the slot
  holds is drawn on a line under it, with the item's own tooltip on the words: the name and the lore, on a
  client with no mod of ours. The item is drawn as it stands rather than typed out with the message. The
  trigger words and the slot names are the BasicShowItem plugin's vocabulary, reimplemented here so that a
  server running NeoForge rather than Paper has them too.
- **Banner.** One line of text stays on screen to the left of the log's menu button, and so above the
  log itself, for as long as the game is in a world. It is off until a text is set, and the text is
  all there is to it: it is written in the same markup as chat — colours, gradients, animations,
  sounds — and drawn with the game's own font, so another mod's inline text works in it too. With
  [Inline] installed, `[item:diamond_sword]`, `[entity:pig]` and the like are drawn as what they name,
  in line with the text; the text is handed to Inline when it is there and drawn as plain markup when
  it is not. `\n` starts a second line, and a text too long for the room it has is wrapped, so it
  never runs under the button.
- **Dice.** `/roll` leaves a line in the action log, and nothing in chat, for the players around
  whoever is being rolled for: an optional selector, an optional maximum and a free-text reason, all
  with configurable defaults. A roll that names nobody is worded as a line of its own, so it never
  reads as a sentence about a player who is not there. The line types itself out with a sound of its
  own, and the lowest and highest results get their own wording and their own sound. `/hroll` leaves
  **nothing** for the players at roll time: the roller gets a private gray copy of the line in their
  own log, with the real number, and the whole line is withheld from everyone else for the server's
  configured delay (three minutes by default). When the delay is up, the player and everyone who
  witnessed the roll around the roller remember it — “You remembered that Dev's dice showed 87/100
  for a search” — in their log; a witness who is offline at that moment finds it in their memory
  when they next open the log. The remembered wording and the sound it plays are settings of their
  own, with separate lowest and highest variants, so a hidden roll can be colour, shake or ring out
  on an extreme without using the public roll's wording.
- **Chat channels.** A plain message goes to local chat, which reaches the players around you; a
  leading `!` sends it to global chat. Staff read every local line wherever they are — an operator in
  another world is still the person a complaint about a line is made to — while everyone else hears
  only what is said near them. `/adm <message>` is the operators' own channel: the real account
  name, the channel separator and exactly the plain text that was typed — no chat profile format,
  typewriter, sound or item triggers — sent to every operator online and to nobody else. Local and
  global each have their own separator and can be open to everyone or restricted to staff; the
  admin channel is always staff-only and has a separator of its own. A player can be silenced in
  local chat, in global chat, for actions, or for private messages.
- **Profiles.** Players write their own chat formats — colour, weight, gradients, animations, an
  optional typing sound — in the corner menu. Operators write display names and manage any player's
  profiles from the administrator menu. A player's markup is checked against an allow-list; an
  operator's is held to its shape only.
- **Effect and delete tools.** The effect tool places a Photon effect: block, block side, entity,
  self or accurate placement, with auto-rotation, a lifetime, a delay, a scale, a rotation and an
  offset, and a preview of where it would land. A block aimed at part of a moving structure — a ship,
  with Sable installed — is stored in that structure's own space, and the effect follows it with the
  position and rotation it was placed with. It is configured by holding `W` over it in any
  inventory. The delete tool takes away what is already placed — the effect under the crosshair, the
  effects on an entity, or everything hanging off its holder. Both fire a shot their holder hears and
  nobody else does. Both are found in the mod's own creative tab rather than given by a command.
- **Item editor.** Operators rewrite an item's name and lore in one screen: the name and the lore in
  the form the item will show them, with a live preview of its tooltip and its own icon, short-named
  formatting buttons that wrap the selected text, and a colour picker beside them. The markup is
  Ember's, so colours, gradients and animations stay animated on the item itself. An **Advanced
  properties...** button opens the second screen: its Properties tab changes attributes, consumable
  stats and effects, durability, dyed colour, enchantments and miscellaneous flags including a block
  item that refuses to be placed; Behaviour runs ordered commands on the server as the holder,
  with operator permission, when the item is used to attack, used or eaten, with a cooldown on the
  first two. The attack trigger can also run a second list as
  the entity that was hit. The advanced edits share
  the main screen's draft, and the Apply at the top of the advanced screen is the same write as the
  main screen's Apply, so either one writes the edit and the main one is not required first.
- **Zone music.** `/playradius` and `/playbox` play a sound to the players inside a region, and
  `/stopzone` ends one.
- **Menu music.** Opening the corner menu plays a track named in the server's config for the player
  who opened it, and fades it out when the menu closes. Set `menuMusic` empty for silence, and
  `menuMusicChannel` to whichever of the game's sound channels it should answer to — the player's own
  slider for that channel is the one that turns it down.
- **Client chest.** `/clientchest` — or `/cc`, or the button in the bottom left corner of the screen —
  opens pages of fifty-four slots kept in this player's own game directory — as many as you store,
  a page existing once it holds something or has a name — so the same
  library follows them from world to world and from server to server. The pages are files, not a
  container: nothing is stored on a server and nothing is asked of one. Items move between a page and
  the player's inventory in creative only, because that is the only place the game accepts an
  inventory the client changed by itself; outside creative the library opens read-only, with the slots
  it will refuse tinted, and a page can still be rearranged, since that never leaves the client. A
  page written by another version of the game is offered as a page rather than read as items: an older
  one can be taken on as this version or discarded, a newer one only discarded, and until then its file
  is not written at all. A slot
  holding an item this game cannot read — a component from a datapack or a mod this world does not
  have — is drawn red and locked rather than dropped or taken as a reason to refuse the page, and
  `Delete` on it forgets that item; the other fifty-three slots work. Space over a page slot opens the
  item editor on it, and there the edit is written into the page rather than sent anywhere, so a slot
  can be named and given lore without a server being involved. `X` empties a page after a second press,
  and `R` reads it from disk again, which is the only way to see a page edited outside the game.

## How this mod was made

**The code is AI-generated**: it was written with an AI coding agent, directed and reviewed by a
human.

**No image is AI-generated**: every texture, sprite, icon, model and drawing in the mod is the
author's own work.

The client chest is not this mod's idea. It comes from **NBT Editor** by mega12345mega, which has a
library of item pages on the client — a chest an item can be put into and taken out of again on another
server — and this is the same idea rewritten for this mod:

- [github.com/mega12345mega/NBT-Editor](https://github.com/mega12345mega/NBT-Editor)
- [curseforge.com/minecraft/mc-mods/nbt-editor](https://www.curseforge.com/minecraft/mc-mods/nbt-editor)

Only the idea: what is here is written for this mod, to this mod's conventions and this mod's file
format, and the two libraries are separate.

## Requirements

Minecraft 1.21.1, NeoForge 21.1.250, Java 21, and:

- [ldlib2] 2.2.41 — the menus and their widgets (required)
- [Ember's Text API] 3.0.3 — every styled line of chat, log and name (required)

Optional, each one switching a feature off rather than breaking anything:

- Photon 2.2.7 — the particles the effect tool places. Without it the tool still configures and
  previews, and nothing is placed.
- [Sable] 2.0.6 — the moving structures a block placement can be attached to, so that an effect on a
  hull follows it. Sable itself is never compiled against and never bundled: this mod builds against
  Sable's own MIT-licensed companion library, which travels inside the jar and answers "no sub-level"
  when Sable is absent, so every other feature behaves exactly as it does without it.
- Symbol Chat 1.2.8 — the item editor's symbol button. It is a Fabric mod, so it needs Sinytra
  Connector and the Forgified Fabric API to load on NeoForge at all.

[ldlib2]: https://github.com/Low-Drag-MC/LDLib2
[Ember's Text API]: https://github.com/TysonTheEmber/EmbersTextAPI
[Sable]: https://modrinth.com/mod/sable

## Configuration

Server settings live in `config/backutils-server.toml` and can also be changed from the
administrator menu's Config tab — the banner's text among them, since it is the server's text that
every player's client draws, and the palette of profile sounds, which is edited there a row to an
entry alongside the `/backutils typing_sounds` commands that do the same. `hidePlayerNameTags` is a
server setting too, and among the menu's rows: what is drawn above a player's head is the server's
rule about the world, so an operator sets it once and every client is sent it, while the tab list, the
chat and the log keep their names. The purely client display settings, including the operator alert's
sound, volume, pitch and icon, are in `config/backutils-client.toml`. The mod's databases
are SQLite files under `serverconfig/backutils/` in the directory a server runs in: the game directory
for a client's integrated server, and the dedicated server's own directory for a real one.

## Commands

Everything is permission level 2, except `/me` and `/sme`: an action line is the players' own, `/me`
replaces the vanilla command so that the log can see it, and neither may be gated without taking a
vanilla command away from the people who have it.

- `/me <action>`, `/sme <action>` — an action line; the silent one is shown to the actor alone
- `/backutils menu`, `actions`, `typing_sounds add|remove|list` — `typing_sounds` is the palette a
  chat profile may name: `add <name> <sound id>` stores `name=soundid` in the server's config, a bare
  name in that list stays what Ember's own preset table resolves, and `list` shows what each name plays
- `/freeze [player]`, `/unfreeze [player]` — `/freeze` holds a player still, or lets them go when they
  are already held; `/unfreeze` only lets them go. Naming nobody is yourself, and the answer is for the
  operators, not for the player it acts on
- `/tabhide <player> true|false` — keeps a player's name out of the tab list and the social
  interactions screen, for everyone, while leaving the player themselves in the world; `/tabhide list`
  says who is hidden. Kept in memory, so a restart shows every name again
- `/backutils banner` — the text every player draws beside the log, shown as it was typed;
  `set <text>` sets it in chat's own markup (`\n` starts a new line) and `clear` draws nothing
- `/adm <message>` — plain text in the operators' channel, prefixed with the real account name and
  the admin separator; only permission level 2 and above can send it or be shown it
- `/chat radius`, `global`, `local`, `separator local|global|admin`, `silence <names...> [<kind> [<true|false>]]`
  — the list is names separated by spaces or commas, or `@a` for every player online; a kind is
  `actions`, `local_chat`, `global_chat` or `msg`, and writing one silences it, while `false` after it
  lifts that one again. A name on its own asks what a player is silenced in
- `/profile chat|name create|edit|delete|use|list`, `reload [all|<player>]`
- `/roll`, `/hroll` — `[selector] [maximum] [reason]`, each part optional. `/hroll` withholds the
  whole line for the server's configured delay and then the players who witnessed it remember it
- `/playradius`, `/playbox`, `/stopzone`, `/log <targets> <message>`
- `/clientchest [<page>|<name>]`, `/cc [<page>|<name>]` — the client chest, pages counted from one;
  `import` moves a library left behind by NBT Editor into this one

The two effect tools are not commands: they are in the mod's own creative tab.

The client chest's command is the exception to the permission line above: the client answers it, the
server never sees it, and it therefore works on a server that has none of this mod.

The banner is a server setting rather than a client one: an operator sets it once and every player is
sent it, and a client without this mod draws none.

## Building

```
./gradlew build
```

The workflow in `.github/workflows/build.yml` does the same.

## Releases

Built jars are attached to the releases on this repository's Releases page. A release is made by
pushing a tag that matches `mod_version` in `gradle.properties`, and one command does all of it:

```
./gradlew releaseVersion                    # 0.1.9 becomes 0.1.10, committed, tagged and pushed
./gradlew releaseVersion -PnewVersion=0.2.0 # or a version chosen by hand
./gradlew releaseVersion -PdryRun=true      # say what would happen, change nothing
./gradlew releaseVersion -Ppush=false       # commit and tag, and push it yourself
```

In IntelliJ the same two are run configurations — **Release (next patch)** and **Release (chosen
version)**, whose version is edited in the configuration's *Script parameters* — so a release is the
green run button. The task stops if anything is uncommitted: the release commit holds the version and
nothing else, so commit your work first. It also stops if the version's tag already exists, and it
builds before it writes anything, so a tag is never placed on a tree that does not compile.

In PowerShell a version has to be quoted, or the shell hands Gradle `0.1.8` as a task name:
`./gradlew releaseVersion "-PnewVersion=0.2.0"`. The IntelliJ configurations are not affected, since
the IDE passes the parameters to Gradle itself.

`mod_version` is the version of everything: the jar's name, the Modrinth version number and the tag
the release is published under. `release.yml` refuses a tag that disagrees with it — that workflow is
the one that publishes, so the check is where the publish would be. `build.yml` is the plain build and
knows nothing of tags: it runs on every push and pull request, tag or not.

`.github/workflows/release.yml` then builds the mod exactly as the development workflow does, and
publishes a release with the jar attached, using the repository's own token — for that part no
third-party action is involved and no outside account becomes a contributor. What a release says comes
from `.github/release-notes.md`, so it is reviewable here rather than typed into a form at release time.

The same tag can put the same jar on Modrinth, and does once the repository is told where. Set both of
these and every release is published in both places:

- a **variable** `MODRINTH_ID` — the Modrinth project's id or slug
- a **secret** `MODRINTH_TOKEN` — a personal access token from
  [modrinth.com/settings/account](https://modrinth.com/settings/account) with the `CREATE_VERSION` scope

With either missing, the step is skipped and a release is the GitHub one alone, which is what happens
by default. It runs after the GitHub release and its failures are warnings rather than errors, so
nothing on Modrinth can stop a release from being built or published here. Modrinth's own route is its
Minotaur Gradle plugin instead, which would put the upload inside the build rather than beside it.

The changelog a Modrinth version gets is the list of commit subjects since the previous tag, not the
release notes: those describe the mod, and a changelog should describe the release.

## Testing on a dedicated server

Half of this mod only ever runs on a server — the log, the databases, the effect the tool hands to
somebody else — and a client with an integrated server does not exercise it the same way, because a
client-only class reached from common code fails on a dedicated server and in an integrated one is
simply there. So the `server` run is a game directory of its own, `run-server/`, meant to be started
*beside* a client rather than instead of one:

```
./gradlew runServer      # in one terminal
./gradlew runClient      # in another, then: Multiplayer -> Direct Connection -> 127.0.0.1:25566
```

The server listens on `127.0.0.1:25566` and nowhere else. Both runs have Sable installed — the client
and the server — because the sub-level support needs it on both sides: the client draws an effect on a
hull, and the server is what knows that the hull is there and refuses a placement whose hull it cannot
find. Sable is put in place by `installSableMods` and `installSableServerMods`, which those two runs
depend on, and `./gradlew cleanSableMods` removes it again. Connect with that literal address rather
than with `localhost`: `localhost` resolves to the IPv6 address first, this server is bound to IPv4,
and on some machines — this one included — an IPv6 socket is refused outright, which arrives as
`java.net.SocketException: Permission denied: getsockopt` and looks like the server's fault when it
is not. The dev client signs in as `Dev`, which `run-server/ops.json` already names as an operator,
so the mod's commands are available at once; `/op <name>` from the server console does the same for
anybody else. A second player can be an ordinary launcher profile with the same mod and its
dependencies installed — a dedicated server needs them too, since the mod declares them.

The settings in `run-server/` are for testing and are not a template for a public server:

- `server-ip=127.0.0.1` — reachable from this machine and from nowhere else.
- `online-mode=false` and `enforce-secure-profile=false` — a dev client has no Mojang session to
  authenticate with, and a server that asked for one would refuse it.
- `enable-rcon=true` on `127.0.0.1:25575`, password `backutils-test` — so commands can be sent from a
  script as well as typed into the console.
- `eula.txt` — **running the server is agreeing to Mojang's EULA**; set it to `false` if you do not
  agree.

`run-server/` is ignored by git, so the world, the logs and the databases stay out of the
repository. Deleting `run-server/world` gets a fresh one.

What is worth checking there: that the server starts with the mod at all, that the databases appear
under `run-server/serverconfig/backutils/`, that the mod's commands are all present and gated as they
should be, and that what one player does is recorded and shown to the players around them — which
takes two clients and is the one thing a single player cannot test.

## Testing with CIT Resewn and RP Renames

RP Renames shows every textured CIT and CEM rename a resource pack provides, and CIT Resewn is what
gives it CITs to show. Neither has a NeoForge build for 1.21.1 — RP Renames' NeoForge line begins at
0.12.0, on a later Minecraft — so on this version both are Fabric jars loaded through Sinytra
Connector, and CITResewnNeoPatcher is the NeoForge mod that keeps CIT Resewn working in that
arrangement. All of it goes into a third game directory of its own:

```
./gradlew runCitResewn
```

`installCitMods` downloads Connector, the Forgified Fabric API, RP Renames, CIT Resewn and the patcher
into `run-citresewn/mods` the first time the run is used, and `cleanCitMods` removes them again. The
versions are properties in `gradle.properties`, so one that the run rejects can be changed without
touching the build file.

RP Renames registers a creative tab whose contents generator adds nothing to it — the renames live in
a list RP Renames draws itself, when its own screen takes over that tab. An empty tab is not drawn on
NeoForge: its creative menu keeps only tabs that have something in them, and leaves the rest in no page
at all, silently. (Fabric shows the same tab, because Fabric's screen accepts an empty one — which is
why this is a NeoForge symptom and not a fault in RP Renames. RP Renames later fixed it upstream, for
its own NeoForge line, by giving the tab one item.)

[RpRenamesTab](src/main/java/net/xlebupaksa/backutils/client/RpRenamesTab.java) does the same thing
from outside, for the build that has no such fix: it offers that tab the item RP Renames uses as its
own icon, and asks it to refill the list behind it, so the tab appears with its renames in it. The tab
is on the second page of the creative menu, behind the `>` button — NeoForge fits ten tabs to a page
and vanilla's come first.

## Testing with Inline

[Inline] draws items, entities, player heads and mod icons in line with text — `[item:diamond_sword]`,
`[entity:pig]`, `[face:notch]`, `[mod:inline]` — by patching the game's own text renderer. That is the
renderer the banner and the log are drawn with, which is why it belongs beside this mod:

- Inline parses a plain `String` as the game decomposes it. A `Component` never goes that way, so the
  banner asks Inline to parse its text and draws what comes back, through
  [InlineSupport](src/main/java/net/xlebupaksa/backutils/client/InlineSupport.java). It is a
  reflective call, the kind this mod uses for every optional mod, so a text with no inline syntax in
  it is drawn exactly as it is without Inline.

It goes into a fourth game directory of its own:

```
./gradlew runInline
```

`installInlineMods` downloads Inline and the Cloth Config it requires into `run-inline/mods` the first
time the run is used, and `cleanInlineMods` removes them again. The versions are properties in
`gradle.properties`. Unlike the other two compatibility runs this one needs no Sinytra Connector,
because Inline publishes a NeoForge build for 1.21.1. Nothing here is compiled against, bundled or
required: Inline is a mod to test beside this one.

What is worth checking there: that `[item:...]`, `[entity:...]`, `[mod:...]` and `[face:...]` are
drawn where the banner puts them, that the lines still end against the button when a symbol is in the
text, and that a banner with none of that syntax in it looks exactly as it does in `runClient`.

[Inline]: https://modrinth.com/mod/inline

## Testing with Mouse Tweaks

[Mouse Tweaks] changes what dragging and shift-clicking mean in a container screen: a stack dragged
with the button held is dropped into every slot it passes over, and a shift-drag fills a whole row. That
is the one thing about the client chest that cannot be checked in the ordinary run, because the chest
answers the clicks itself — its menu exists only on the client and no click is ever sent — so a mod
that synthesises clicks and drags against the open menu is what its handling has to survive.

It goes into a fifth game directory of its own:

```
./gradlew runMouseTweaks
```

`installMouseTweaksMods` downloads Mouse Tweaks into `run-mousetweaks/mods` the first time the run is
used, and `cleanMouseTweaksMods` removes it again. The version is a property in `gradle.properties`.
Nothing here is compiled against, bundled or required: it is a mod to test beside this one.

What is worth checking there: that a stack dragged across a page's slots lands where Mouse Tweaks would
put it, that a shift-drag fills a row, and that outside creative nothing crosses between a page and the
player's inventory however it is dragged.

[Mouse Tweaks]: https://modrinth.com/mod/mouse-tweaks

## Licence

GPL-3.0-only, copyright (C) 2026 xlebupaksa. The full text is in [LICENSE](LICENSE).

The released jar carries two pieces of somebody else's code inside it, both bundled whole and
unmodified by `jarJar`, with their licence texts inside the nested jars: sqlite-jdbc, copyright the
Xerial project, under the Apache License 2.0; and Sable Companion, copyright RyanHCode, under the MIT
Licence. The second is the whole of this mod's Sable support — the compatibility library Sable's own
wiki points a third-party mod at, whose calls answer "no sub-level" when Sable is not installed. Sable
itself is under PolyForm Shield, is neither compiled against nor bundled here, and no code from it is
copied. Nothing else here is bundled, and the mods this one is tested alongside — ldlib2, Photon,
Sable, Ember's Text API, Connector, the Forgified Fabric API, Symbol Chat, RP Renames, CIT Resewn and
CITResewnNeoPatcher — are separate downloads that are never redistributed by this project. Sable is
downloaded into the two default runs and nowhere else, and those folders are ignored by git.

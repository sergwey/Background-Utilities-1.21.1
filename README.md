# Background Utilities

A NeoForge mod for roleplay servers: a witness-based action log, two chat channels, chat and
display-name profiles written in markup, an operator item editor, a banner line that stays beside the
log, and a pair of tools for placing and removing visual effects.

## What it adds

- **Action log.** `/me` and `*asterisk*` lines — `^caret^` for silent ones — are recorded with every
  player who was within the witness radius, and shown to them in an on-screen log. Staff review,
  hide or delete entries from the administrator menu, and each action raises an alert for operators.
- **Banner.** One line of text stays on screen to the left of the log's menu button, and so above the
  log itself, for as long as the game is in a world. It is off until a text is set, and the text is
  all there is to it: it is written in the same markup as chat — colours, gradients, animations,
  sounds — and drawn with the game's own font, so another mod's inline text works in it too. With
  [Inline] installed, `[item:diamond_sword]`, `[entity:pig]` and the like are drawn as what they name,
  in line with the text; the text is handed to Inline when it is there and drawn as plain markup when
  it is not. `\n` starts a second line, and a text too long for the room it has is wrapped, so it
  never runs under the button.
- **Dice.** `/roll` and `/hroll` leave a line in the action log, and nothing in chat, for the players
  around whoever is being rolled for: an optional selector, an optional maximum and a free-text
  reason, all with configurable defaults. A roll that names nobody is worded as a line of its own,
  so it never reads as a sentence about a player who is not there. The line types itself out with a
  sound of its own, and the lowest and highest results get their own wording and their own sound. A
  hidden roll shows everyone an obfuscated result and keeps the real one for operators, in their own
  log and in the administrator menu; it is never dressed and never sounds an extreme, since either
  would say which number came up.
- **Chat channels.** A plain message goes to local chat, which reaches the players around you; a
  leading `!` sends it to global chat. Each channel has its own separator and can be open to
  everyone or restricted to staff, and a player can be silenced per channel or for actions.
- **Profiles.** Players write their own chat formats — colour, weight, gradients, animations, an
  optional typing sound — in the corner menu. Operators write display names and manage any player's
  profiles from the administrator menu. A player's markup is checked against an allow-list; an
  operator's is held to its shape only.
- **Effect and delete tools.** The effect tool places a Photon effect: block, block side, entity,
  self or accurate placement, with auto-rotation, a lifetime, a delay, a scale, a rotation and an
  offset, and a preview of where it would land. It is configured by holding `W` over it in any
  inventory. The delete tool takes away what is already placed — the effect under the crosshair, the
  effects on an entity, or every effect its holder placed. Both fire a shot their holder hears and
  nobody else does. Both are found in the mod's own creative tab rather than given by a command.
- **Item editor.** Operators rewrite an item's name, lore and attributes in one screen: the name and
  the lore in the form the item will show them, with a live preview of its tooltip and its own icon,
  short-named formatting buttons that wrap the selected text, and a colour picker beside them. The
  markup is Ember's, so colours, gradients and animations stay animated on the item itself.
- **Zone music.** `/playradius` and `/playbox` play a sound to the players inside a region, and
  `/stopzone` ends one.
- **Menu music.** Opening the corner menu plays a track named in the server's config for the player
  who opened it, and fades it out when the menu closes. Set `menuMusic` empty for silence.

## How this mod was made

**The code is AI-generated**: it was written with an AI coding agent, directed and reviewed by a
human.

**No image is AI-generated**: every texture, sprite, icon, model and drawing in the mod is the
author's own work.

## Requirements

Minecraft 1.21.1, NeoForge 21.1.250, Java 21, and:

- [ldlib2] 2.2.41 — the menus and their widgets (required)
- [Ember's Text API] 3.0.3 — every styled line of chat, log and name (required)

Optional, each one switching a feature off rather than breaking anything:

- Photon 2.2.7 — the particles the effect tool places. Without it the tool still configures and
  previews, and nothing is placed.
- Symbol Chat 1.2.8 — the item editor's symbol button. It is a Fabric mod, so it needs Sinytra
  Connector and the Forgified Fabric API to load on NeoForge at all.

[ldlib2]: https://github.com/Low-Drag-MC/LDLib2
[Ember's Text API]: https://github.com/TysonTheEmber/EmbersTextAPI

## Configuration

Server settings live in `config/backutils-server.toml` and can also be changed from the
administrator menu's Config tab; client display settings — including the operator alert's sound,
volume, pitch and icon, and the banner's text — are in `config/backutils-client.toml`. The mod's
databases are SQLite files under `serverconfig/backutils/` in the directory a server runs in: the
game directory for a client's integrated server, and the dedicated server's own directory for a real
one.

## Commands

Everything is permission level 2:

- `/me <action>`, `/sme <action>` — an action line; the silent one is shown to the actor alone
- `/backutils menu`, `radius`, `actions`, `typing`, `freeze`, `unfreeze`,
  `profiles sound add|remove|list`
- `/chat radius`, `global`, `local`, `separator local|global`, `silence`
- `/profile chat|name create|edit|delete|use`, `reload`
- `/roll`, `/hroll` — `[players] [maximum] [reason]`, each part optional; `/hroll` hides the result
- `/playradius`, `/playbox`, `/stopzone`, `/log <targets> <message>`

The two effect tools are not commands: they are in the mod's own creative tab.

One command is answered by your own client rather than the server, because it changes only what your
game draws and has to work on a server without this mod. It needs no permission, is answered in your
chat, and is the only `/backutils` path the client takes for itself — every other one is sent to the
server as before:

- `/backutils banner` — the text it currently draws, shown as it was typed
- `/backutils banner set <text>` — set it, in chat's own markup; `\n` starts a new line
- `/backutils banner clear` — draw nothing

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
the release is published under. `build.yml` and `release.yml` both refuse a tag that disagrees with it.

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

The server listens on `127.0.0.1:25566` and nowhere else. Connect with that literal address rather
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

## Licence

GPL-3.0-only, copyright (C) 2026 xlebupaksa. The full text is in [LICENSE](LICENSE).

The released jar carries one piece of somebody else's code inside it: sqlite-jdbc,
copyright the Xerial project, under the Apache License 2.0, bundled whole and unmodified by `jarJar`.
Its licence text travels with it, inside the nested jar. Nothing else here is bundled, and the mods
this one is tested alongside — ldlib2, Photon, Ember's Text API, Connector, the Forgified Fabric API,
Symbol Chat, RP Renames, CIT Resewn and CITResewnNeoPatcher — are separate downloads that are never
redistributed by this project.

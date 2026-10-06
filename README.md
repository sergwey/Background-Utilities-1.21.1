# Background Utilities

A NeoForge mod for roleplay servers: a witness-based action log, two chat channels, and chat and
display-name profiles written in markup.

## What it adds

- **Action log.** `/me` and `*asterisk*` lines — `^caret^` for silent ones — are recorded with every
  player who was within the witness radius, and shown to them in an on-screen log. Staff review,
  hide or delete entries from the administrator menu, and each action raises an alert for operators.
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
- **Zone music.** `/playradius` and `/playbox` play a sound to the players inside a region, and
  `/stopzone` ends one.
- **Menu music.** Opening the corner menu plays a track named in the server's config for the player
  who opened it, and fades it out when the menu closes. Set `menuMusic` empty for silence.

## Requirements

Minecraft 1.21.1, NeoForge 21.1.250, Java 21, and:

- [ldlib2] 2.2.41 — the menus and their widgets (required)
- [Ember's Text API] 3.0.3 — every styled line of chat, log and name (required)

[ldlib2]: https://github.com/Low-Drag-MC/LDLib2
[Ember's Text API]: https://github.com/TysonTheEmber/EmbersTextAPI

## Configuration

Server settings live in `config/backutils-server.toml` and can also be changed from the
administrator menu's Config tab; client display settings are in `config/backutils-client.toml`.
The mod's databases are SQLite files under `<world>/serverconfig/backutils/`.

## Commands

Everything is permission level 2:

- `/backutils menu`, `radius`, `actions`, `typing`, `profiles sound add|remove|list`
- `/chat radius`, `global`, `local`, `separator local|global`, `silence`
- `/profile chat|name create|edit|delete|use`, `reload`
- `/roll`, `/hroll` — `[players] [maximum] [reason]`, each part optional; `/hroll` hides the result
- `/playradius`, `/playbox`, `/stopzone`, `/log`

## Building

```
./gradlew build
```

The workflow in `.github/workflows/build.yml` does the same.

**Background Utilities** adds a witness-based action log, dice, chat and display-name profiles, two
chat channels, and an operator's tools for placing and removing visual effects — for NeoForge
1.21.1.

## Install

1. Minecraft **1.21.1** with **NeoForge 21.1.250** or newer.
2. Put the jar from this release into your `mods/` folder, together with:
   - **ldlib2 2.2.41 or newer** — required, the menus and their widgets
   - **Ember's Text API 3.0.3 or newer** — required, every styled line of chat, log and name
   - **Photon 2.2.7 or newer** — optional; without it the effect tool configures and previews but
     places nothing
   - **Symbol Chat** — optional; it is a Fabric mod, so it also needs Sinytra Connector and the
     Forgified Fabric API, and all it adds is the item editor's symbol button
3. On a server, the same jars go in the server's `mods/` folder. The mod keeps its databases in
   `serverconfig/backutils/`, inside the directory the server runs in.

Everything the mod adds is documented in the [README](https://github.com/sergwey/Background-Utilities-1.21.1#readme).

## How this mod was made

**The code is AI-generated** — written with an AI coding agent, directed and reviewed by a human.

**No image in this mod is AI-generated.** Every texture, sprite, icon, model and drawing is the
author's own work. The README's provenance section has the rest, including which sound files are not
the author's own.

## Reporting a problem

Issues belong in this repository. Please include the game log from a run that shows the problem, and
say whether it happened in single player or on a server: half of this mod only does anything on a
server.

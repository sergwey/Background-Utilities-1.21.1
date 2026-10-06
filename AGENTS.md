# Working in this repository

Background Utilities is a NeoForge mod for roleplay servers: a witness-based action log, dice, chat
and display-name profiles, two chat channels, an operator item editor, and two tools that place and
remove visual effects. Minecraft 1.21.1, NeoForge 21.1.250, Java 21, ldlib2 and Ember's Text API.

This file is for whoever — or whatever — works on it next, and says the things that are not visible
from the code and are expensive to learn by breaking them.

## Build and run

```
./gradlew build        # the whole build
./gradlew runClient    # a dev client, game directory run/
./gradlew runServer    # a dedicated server in run-server/, meant to run beside a client
./gradlew runSymbolChat # the client with Sinytra Connector and Symbol Chat, game directory run-symbolchat/
```

`run/` and `run-server/` are separate game directories on purpose; the README explains the test
server, including the address to connect to.

## What "it works" has to mean

- **`./gradlew build` passing is not the same as the mod loading, and neither is javac passing.** The
  metadata template, `src/main/templates/META-INF/neoforge.mods.toml`, is expanded by the
  `generateModMetadata` task from the `replaceProperties` map in `build.gradle`. Every `${...}` used
  in that template has to be a key in that map, or the build fails and the only symptom in the game is
  a mod that is not there; write the value into the template when it is a fixed one, as the author
  field does.
- **The mod must load on a dedicated server, not only in a client.** Half of it is server-side — the
  databases, the log, what a tool does for somebody else — and a client-only class reached from
  common code fails on a dedicated server while being perfectly invisible in a client.
- **Say what was observed, not what was intended.** "It compiles" and "it should work" are not
  evidence. If something was not run, say that it was not run.

## Conventions

- British spelling in comments, documentation and language files.
- Comments and javadoc say *why*, not what. Where a decision looks arbitrary, the reason belongs
  beside it.
- The mod writes nothing to the log in normal operation, and logs an error only when something is
  wrong. No debug prints.
- Every language key goes in both `src/main/resources/assets/backutils/lang/en_us.json` and
  `ru_ru.json`.
- Never soften, skip or delete a check to make it pass. Correcting a check that pinned behaviour
  which never actually happened is different, and is how several of them were fixed — say so when
  that is what is happening.
- Item models are 3D and deliberately carry no `parent`: `minecraft:item/handheld` leads to
  `builtin/generated`, whose item model generator throws the elements away.

## Assets, and how this mod was made

- **The code is AI-generated**, written with an AI coding agent under human direction and review.
- **No image is AI-generated.** Every texture, sprite, icon, model and drawing is the author's own
  work. Do not add AI-generated images to `src/main/resources`.
- The statement above is kept in three places, and they must agree: the README's "How this mod was
  made" section, `.github/release-notes.md`, and the `credits` field of the metadata template (the
  one place it is visible inside the game).

## Versions and releases

`mod_version` in `gradle.properties` is the version of the jar and of the release. A release is made
by pushing a tag whose name matches it:

```
# set mod_version=0.2.0, commit, then:
git tag v0.2.0
git push origin v0.2.0
```

`.github/workflows/release.yml` builds the mod with the same build the development workflow runs and
publishes a GitHub release with the jar attached, using the repository's own token — no third-party
action and no contributor is added. The release notes are `.github/release-notes.md`, so what a
release says is reviewable in the repository rather than typed into a form.

## Checks that exist here

The maintainer keeps a throwaway harness under `build/tmp/` — a compile check, a lint pass, a
metadata check and a set of `main()` suites covering the markup, the profile storage, the effect
tool, the item editor and the tool sounds. It is not committed, because it is regenerated as the
work needs it, so a fresh clone will not have it. If `build/tmp/` is present, run what is there
before claiming a change is done; if it is not, build, run the game, and say what was run.

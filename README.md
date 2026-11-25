# Keycaps

A visual keyboard map of your key bindings, for Fabric. **1.0.0 in testing (Minecraft 26.3 first).**

Press **Home** (changeable in Key Binds) in game, on the title screen or on the pause screen. What it does:
- A keyboard drawn in the game with every bound key coloured by group, short captions on the keys and
  everything bound to a key in its hover text (with the mod's name; conflicts in red).
- Reads all bindings live: Minecraft's own key bindings (and so every mod that uses the Key Binds menu),
  Minecraft's debug combinations (F3 + …), **Multi Key Bindings**' extra bindings and modifiers, and
  **malilib** hotkeys (MiniHUD, Tweakeroo, Litematica, …).
- Layers for Ctrl +, Alt + and F3 + combinations; holding the real key switches layer.
- Key labels follow the player's keyboard layout (QWERTY, QWERTZ, AZERTY, …); keyboard shape ANSI, ISO
  or JIS (detected, or set by hand).
- Opens with its own key (Home), also on the pause and title screens, and from other mods through
  `io.github.hiveko.keycaps.KeycapsApi.open(Screen parent)` (call it through reflection to keep Keycaps
  optional).
- Colour groups: `groups.json` in the jar; a copy in `config/keycaps/groups.json` replaces it. Keyboard
  shape: `config/keycaps.json` (`auto`, `ansi`, `iso`) or the button on the screen.

## Files

One jar per Minecraft version: `keycaps-*+mc26.3.jar`, `+mc26.2`, `+mc26.1.2`, `+mc1.21.11`,
`+mc1.21.1` (1.21 and 1.21.1). Needs Fabric API.

## Building

GitHub Actions: *Build* builds every jar; *Release* builds them and publishes a release with the
version's `CHANGELOG.md` section as notes. Locally (Java 25 and 21):

```
./gradlew build -Pmc=26.3        # or 26.2, 26.1.2 -> build/libs
./gradlew -p mc1.21.11 build     # -> mc1.21.11/build/libs
./gradlew -p mc1.21.1 build      # -> mc1.21.1/build/libs
```

Client-side only. License: All rights reserved.

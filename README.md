# Keycaps

A visual keyboard map of your key bindings, for Fabric. **1.0.0 in testing (Minecraft 26.3 first).**

Press **Home** (changeable in Key Binds) in game, on the title screen or on the pause screen. What it does:
- A full keyboard with the numeric keypad (left out when the window is too small), drawn in a window that
  fills the screen with a margin, with every bound key coloured by group, short captions on the keys and
  everything bound to a key in its hover text (with the mod's name; conflicts in red). Home closes it again.
- Reads all bindings live: Minecraft's own key bindings (and so every mod that uses the Key Binds menu),
  Minecraft's debug combinations (F3 + …), **Multi Key Bindings**' extra bindings and modifiers, and
  **malilib** hotkeys (MiniHUD, Tweakeroo, Litematica, …): single keys on the plain keyboard,
  combinations on their own tabs ("B +" for Litematica's B, C sequences; "Z +" for Tweakeroo's Z, 1).
- Layers for Ctrl +, Alt +, Shift + and F3 + combinations and for malilib sequences (B +, Z +, …);
  holding the real key switches layer. Litematica, Tweakeroo, MiniHUD and Axiom have their own colours.
- Clicking a key opens Key Binds; for Litematica, MiniHUD, Tweakeroo and other malilib hotkeys it opens that
  mod's own settings (hotkeys tab).
- Click a colour in the legend to see only those keys (click again for all); **Conflicts** shows only the
  keys whose actions clash.
- **Smart conflict check** (button "Smart: ON/OFF", on by default): a shared key only counts as a conflict
  when both actions can work at the same time: in the world, in menus, in chat (the inventory key on E and
  a sort key used only in the inventory don't clash; Debug Crash only works when F3 + C is held for 10
  seconds, so it doesn't clash with Copy Location). malilib says this for its hotkeys; for the others it
  comes from `situations.json` (a copy in `config/keycaps/situations.json` replaces it), which can also say
  that a mod adds a modifier itself (the emoji picker is Ctrl + E, so it is shown on the Ctrl tab). OFF: every
  shared key counts, as in Minecraft's Key Binds.
- **Switched-off features:** a key whose feature is turned off does nothing, so it is never a conflict; its
  hover text says "switched off" and which option turns it on. Read live from the mods' settings when the
  map opens, through the rules' `needs` in `situations.json` (`<class>#<static field>`, a malilib option or
  a boolean field). Built in: Tweakeroo's flexible and accurate placement, hotbar swap and scroll,
  inventory preview, inventory peek, Y mirror and elytra camera keys (each needs its tweak); MiniHUD's
  inventory preview keys (Inventory Preview enabled); Litematica's Easy Place key (Easy Place mode), its
  pick block keys (pick block on, schematic rendering on) and its tool item keys and modifiers (tool item
  enabled; they only work while holding the tool item, so they don't clash with ordinary keys either).
  Every masa hotkey was checked in the mods' code; `docs/masa-hotkeys.md` lists the result and how to check a
  new mod version with `tools/masa_gates.py`.
- **Made to share a key:** a rule's `shares` lists the actions an action is made to share its key with; such a
  pair is never a conflict. Built in: Litematica's schematic pick block keys and Minecraft's Pick Block
  (Litematica only acts when a schematic block is aimed at).
- **Right-click a key** with more than one action: a list of its pairs, where you tick the pairs that are
  fine. The key stays a conflict while an unticked pair is left; a tick stops counting once one of the two
  actions moves to another key. Stored as `notConflicts` in `config/keycaps.json`.
- Key labels follow the player's keyboard layout (QWERTY, QWERTZ, AZERTY, …); keyboard shape ANSI, ISO
  or JIS (detected, or set by hand).
- Opens with its own key (Home), also on the pause and title screens, and from other mods through
  `io.github.hiveko.keycaps.KeycapsApi.open(Screen parent)` (call it through reflection to keep Keycaps
  optional).
- Action names on the keys in small text (50% of normal by default; slider on the screen).
- Colour groups: `groups.json` in the jar; a copy in `config/keycaps/groups.json` replaces it. Keyboard
  shape (`keyboardShape`: `auto`, `ansi`, `iso`), text size (`captionSize`, percent), `smartConflicts`
  and `notConflicts`: `config/keycaps.json`, or the buttons, slider and right-click on the screen.

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

Client-side only. License: All rights reserved. The icon is "Keyboard" from Microsoft's
[Fluent Emoji](https://github.com/microsoft/fluentui-emoji) (MIT License; the notice is in the jar as
`assets/keycaps/icon-LICENSE.txt`).

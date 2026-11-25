# Changelog

## 1.0.0 (not released yet)

First version, Minecraft 26.3 (the other versions follow).

- **Keyboard map** (key: Home, which also closes it; also on the title and pause screens): a full keyboard
  with the numeric keypad and the three mouse buttons, in a window that fills the screen with a margin
  (like Better Advancements; the keypad is left out when the window is too small for it). Every bound
  key is coloured by group, with a short caption on it and everything on it in the hover text with the
  mod it comes from. Two actions on the same key in the same layer show in red.
- **Layers:** tabs for plain keys, Ctrl +, Alt +, Shift + (when used) and the debug key (F3 +). Holding the
  real Ctrl, Alt, Shift or F3 key shows that layer. On the plain layer a key that only works with a
  modifier shows it in grey ("Ctrl: Drop").
- **Reads every binding live:** Minecraft's key mappings (and so every mod in the Key Binds menu), the
  debug combinations, Multi Key Bindings' extra bindings and modifiers, and malilib hotkeys (MiniHUD,
  Tweakeroo, Litematica, …; only hotkeys on a single key, combinations like Ctrl + X or "hold B, press C"
  are left out). Keys the drawing doesn't have (numpad, extra mouse buttons) are listed under the keyboard.
- **Action names in small text** (50% of normal by default) with a slider in the footer; the steps are whole
  screen pixels, so the text stays sharp, and as many lines as fit (up to three) are used.
- **Keyboard layout:** the letters on the keys follow the system layout (QWERTZ, AZERTY, …). The shape
  is ANSI or ISO, guessed from the computer's region or chosen with the button (`config/keycaps.json`).
- Clicking a key opens Key Binds; coming back refreshes the map. Pressing the open key again closes the map.
- Colour groups and their rules are in `groups.json`; copy it to `config/keycaps/groups.json` to change
  them. Short captions are in the language files (`keycaps.short.<action>`). English and Hungarian.
- For other mods: `io.github.hiveko.keycaps.KeycapsApi.open(Screen parent)`.

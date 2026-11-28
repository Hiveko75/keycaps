# Changelog

## 1.0.0 (not released yet)

First version, for Minecraft 26.3, 26.2, 26.1.2, 1.21.11 and 1.21.1.

- **Keyboard map** (key: Home, which also closes it; also on the title and pause screens): a full keyboard
  with the numeric keypad and the three mouse buttons, in a window that fills the screen with a margin
  (like Better Advancements; the keypad is left out when the window is too small for it). Every bound
  key is coloured by group, with a short caption on it and everything on it in the hover text with the
  mod it comes from. Actions that clash on the same key show in red.
- **Hover text:** with Smart on, only the key's actions and the mod they come from ("Pick Block
  Litematica"). With Smart off it also says when an action works ("in menus"), its notes ("Works only
  while … is on", "Hold … too") and the click hints. With Smart on, the debug combinations (F3 +) are
  only listed and shown on the keys while the F3 + tab is selected or F3 is held.
- **Colour legend:** choosing a group (Litematica, Tweakeroo …) shows all of its keys in its colour, on the
  plain layer also those it uses with Ctrl, Alt, Shift or a sequence key ("Ctrl: Save area"), and the hover
  text lists only that group's actions. Keys whose actions are switched off now (an option they need is
  off) are dark, with their action in grey; this also applies without a group chosen.
- **Hover text per layer:** with a modifier tab selected or the modifier held (Ctrl +, Alt +, Shift +, F3 +,
  B + …) the hover text lists only that layer's actions; on the plain layer it lists all of them.
  The modifier key itself keeps its own actions on its layer (holding Alt still shows what Alt does, such
  as Litematica's Selection Nudge Modifier), in its caption and hover text.
- **Fly Speed Modifier** (Vanilla: Left Alt + mouse wheel): its key only works while flying in Creative or
  Spectator, or in Freecam, so it only clashes with actions that also work then.
- **Keyboard: Auto** picks ISO (with the extra key next to left Shift, Í on Hungarian keyboards) from the game's
  language (hu_hu → Hungary) or the computer's region format, not only from the display language: Windows
  in English with a Hungarian keyboard showed ANSI and had no Í key.
- **Mouse above the keypad:** left, middle and wheel (one double button: click and scroll), right, with a frame
  around them for mouse movement. The wheel and movement show what they do on each layer, following the held
  key's binding: hotbar and looking around, Leawind's camera distance and offset (Adjust Camera), Fly Speed
  Modifier's flight speed, Scroll-Walk's walking speed, Zoomify's zoom level. Without the keypad the three
  buttons stay under Delete / End / Page Down and the wheel and movement are listed under "Elsewhere".
- **Key labels** are one size on every key, a little smaller when one would not fit its key ("Wheel", "Közép").
- **ISO Enter** is drawn as one key (the wide upper part and the narrower lower part without a line between
  them, its name once).
- **Keyboard: Auto** on Windows follows the keyboard layout in use (Hungarian → ISO with the Í key, US → ANSI),
  whatever the game's language; elsewhere as before. Resource pack variants of a language (Colourful
  Containers' "en_us-showtext") count as that language.
- **Layers:** tabs for Ctrl +, Alt +, Shift + (when used) and the debug key (F3 +); with no tab selected the
  map shows the keys without a modifier, and clicking the selected tab again goes back there. Holding the
  real Ctrl, Alt, Shift or F3 key shows that layer; holding the left or the right one shows only the actions
  for that side (and those that work with either), and left/right bindings of the same key are no conflict.
  Right Alt counts as Alt also when Windows reports Ctrl with it (AltGr). The first keys of malilib
  sequences ("B +", "Z +") have tabs but are ordinary keys: pressing them doesn't switch the map. On the
  plain layer a key that only works with a modifier shows it in grey ("Ctrl: Drop").
- **Every version:** the same map on 26.3, 26.2, 26.1.2, 1.21.11 and 1.21.1. Before 1.21.9 the F3
  combinations are not key mappings, so 1.21.1 lists Minecraft's fixed ones (F3 + A, B, C, D, G, H, I, L, N,
  P, Q, S, T, F4, Esc, 1, 2, 3) with the names from its F3 + Q help. On GLFW versions (26.2 and older) the
  extra ISO key is world.2 on Windows and world.1 elsewhere.
- **Reads every binding live:** Minecraft's key mappings (and so every mod in the Key Binds menu), the
  debug combinations, Multi Key Bindings' extra bindings and modifiers, and malilib hotkeys (MiniHUD,
  Tweakeroo, Litematica, …). The plain keyboard shows single keys only; combinations have their own tabs:
  Ctrl / Alt / Shift +, F3 +, and one tab per first key of a sequence ("B +" for Litematica's B, C; "Z +"
  for Tweakeroo's Z, 1). Holding that key shows its tab. Litematica, Tweakeroo, MiniHUD and Axiom have their
  own colours.
- **Filters:** clicking a colour in the legend shows only that group's keys (click it again for all keys);
  the **Conflicts: N** button shows only the keys whose actions clash in the layer shown.
- **Smart conflict check** ("Smart: ON", switchable): a shared key only counts as a conflict when both
  actions can work at the same time (in the world, in menus, in chat). malilib hotkeys say this
  themselves; `situations.json` says it for the rest (inventory, hotbar, drop and offhand keys also work
  in menus; ClientSort, Locked In Slots and OptiGUI's inspect key only in menus; Jade's details key only
  changes what is shown; Flashback's timeline keys only in its editor; Minecraft's Debug Crash only when
  F3 + C is held for 10 seconds, so it doesn't clash with Copy Location) and that the emoji picker is really
  Ctrl + E (shown on the Ctrl tab). The hover text says when an action works if it is not just in the world.
- **Switched-off features don't count as conflicts:** Tweakeroo's flexible and accurate placement, hotbar
  swap and scroll, inventory preview, inventory peek, Y mirror and elytra camera keys only work while their
  tweak is on; MiniHUD's inventory preview keys while Inventory Preview is enabled; Litematica's Easy Place
  key in Easy Place mode, its pick block keys while pick block and schematic rendering are on, and its tool
  item keys and modifiers while the tool item is enabled (and only while holding it, so they don't clash
  with ordinary keys either). Read live when the map opens; the hover text says "switched off" and which
  option turns the key on, and the right-click list says "one of them is switched off". Other mods can be
  added with `needs` in `situations.json`.
  Every Tweakeroo, MiniHUD and Litematica hotkey was checked in the mods' code (list and reasons in
  `docs/masa-hotkeys.md`): also Tweakeroo's spyglass key (Spyglass Uses Tweak Zoom) and area selection offset
  (Area Selector), Litematica's version cycle modifier (tool item), and keys that only change what is shown
  while held (MiniHUD's required key; Litematica's render-through-blocks, invert rendering and info overlay
  keys). Litematica's block-pick modifiers (used with a click) and its scroll-wheel modifiers share keys by
  design (both on Left Alt by default), and its Easy Place key works through Minecraft's Use key.
- **Icon:** the keyboard from Microsoft's Fluent Emoji (MIT License, notice in the jar).
- **Fixed keys:** keys that mods read directly instead of registering them are shown on the map ("Fixed in
  …", not in Key Binds) and checked for conflicts: Minecraft's Ctrl + Drop (drops the whole stack, so a Drop
  binding that itself needs Ctrl always drops the whole stack) and Ctrl + Pick Block (with the block's data,
  creative), Scroll Walk's Alt + mouse wheel (while it is enabled), HeyWiki's Alt + Open Wiki (the held item),
  Xaero's World Map's Shift + Toggle Dimension (backwards) and Language Reload's F3 + Shift + J (cycle
  languages). Found by scanning every mod of the 26.3 pack; listed under `fixed` in `situations.json`.
- **F3 is not a conflict:** the debug screen key and the debug modifier (both F3) are one entry again, as
  meant ("Hold it for the debug combinations"); Minecraft keeps them outside its list of debug combinations,
  so both were shown on F3 as a clash.
- **Made to share a key:** `situations.json` rules can say which actions an action is made to share its key
  with (`shares`). Litematica's schematic pick block keys only act when a schematic block is aimed at and
  leave the key to Minecraft's Pick Block otherwise, so the two never count as a conflict (the right-click
  list says "made to share this key").
- **Multi Key Bindings' extra bindings** are named after their action (a second Auto-Jump Toggle binding on
  the middle mouse button showed as "Middle Button", the name of its key).
- **Right-click a key** with more than one action to tick the pairs that are not conflicts; the key stays a
  conflict while an unticked pair is left. Saved in `config/keycaps.json` (`notConflicts`).
  Keys the drawing doesn't have (extra mouse buttons, F13 and up) are listed under the keyboard.
- **Action names in small text** (50% of normal by default) with a slider in the footer; the steps are whole
  screen pixels, so the text stays sharp, and as many lines as fit (up to three) are used.
- **Keyboard layout:** the letters on the keys follow the system layout (QWERTZ, AZERTY, …). The shape
  is ANSI or ISO, guessed from the computer's region or chosen with the button (`config/keycaps.json`).
- Clicking a key opens Key Binds, or, for a malilib hotkey (Litematica, MiniHUD, Tweakeroo, …), that mod's
  own settings on its hotkeys tab; coming back refreshes the map. Pressing the open key again closes the map.
- Colour groups and their rules are in `groups.json`; copy it to `config/keycaps/groups.json` to change
  them. Short captions are in the language files (`keycaps.short.<action>`). English and Hungarian.
- For other mods: `io.github.hiveko.keycaps.KeycapsApi.open(Screen parent)`.

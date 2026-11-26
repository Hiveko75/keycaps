# masa hotkeys: which ones only act while an option is on

Checked in Tweakeroo 0.30.1, MiniHUD 0.41.2 and Litematica 0.29.1 (Minecraft 26.3) with
`tools/masa_gates.py` (below), then by reading the code of every case the script could not decide.
malilib has no setting that links a hotkey to the option it depends on; the link only exists in each mod's
code, so the result is kept as rules in `src/main/resources/assets/keycaps/situations.json`. If a mod renames
an option, Keycaps cannot read it and treats the key as always on (as before), and logs it once.

## In situations.json

**Needs an option (`needs`): switched off = never a conflict**
- Tweakeroo, each needs its tweak: flexibleBlockPlacementAdjacent / Offset / Rotation (Flexible Block
  Placement), accurateBlockPlacementInto / Reverse (Accurate Block Placement), hotbarSwap1-3 and hotbarSwapBase
  (Hotbar Swap), hotbarScroll (Hotbar Scroll), inventoryPreview and inventoryPreviewToggleScreen (Inventory
  Preview), playerInventoryPeek (Player Inventory Peek), placementYMirror (Y Mirror), elytraCamera (Elytra
  Camera), swapSpyglassAndActivate (Spyglass Uses Tweak Zoom), areaSelectionOffset (Area Selector).
- MiniHUD: inventoryPreview and inventoryPreviewToggleScreen (Generic: Inventory Preview Enabled).
- Litematica: easyPlaceUseKey (Easy Place Mode); pickBlockFirst / Last (Pick Block Enabled, rendering and
  schematic rendering on; made to share Minecraft's Pick Block key); the tool item keys and modifiers
  toolPlaceCorner1/2, toolSelectElements, toolSelectModifierBlock1/2, operationModeChangeModifier,
  selectionNudge/Grab/GrowModifier and schematicVersionCycleModifier (Tool Item Enabled, rendering on; they
  also only act while holding the tool item: situation `litematica_tool`).

**Only changes what is shown while held (`passive`): never a conflict**
- MiniHUD: requiredKey (the info lines only show while it is held).
- Litematica: renderOverlayThroughBlocks, invertGhostBlockRenderState, invertOverlayRenderState,
  renderInfoOverlay.

## Not in the rules: these act whatever the options say
- Tweakeroo: flyPreset1-4 and flyIncrement1/2 (change the fly speed setting even with Fly Speed off),
  breaking/placementRestrictionMode* (change the mode), zoomActivate (adjusts the mouse sensitivity even with
  Zoom off), freeCameraPreset*, freeCameraPlayerInputs/Movement, copySignText, toolPick, swapElytraChestplate,
  areaSelectionAddToList / RemoveFromList, sit down / stand up pets, skipWorldRendering, writeMapsAsImages,
  toggleGrabCursor, toggleAccuratePlacementProtocol, openConfigGui, openCameraPresetEditorGui.
- MiniHUD: openConfigGui, shapeEditor, moveShapeToPlayer, setDistanceReferencePoint.
- Litematica: the GUI keys, layer keys, save / clone / unload, rerenderSchematic, placement rotation / mirror.
  Some only do something in a tool mode or with a schematic project open (the area selection keys in the
  area selection modes, schematicEdit* modifiers in Schematic Edit mode, schematicVersionCycleNext/Previous
  and schematicVCSDeleteBlockByPlacement with a project open): that is the current tool mode, not an option,
  so they stay counted.
- Every hotkey that switches an option itself (tweak toggles, Disable toggles, MiniHUD info lines and
  renderers, Litematica's toggle* keys, easyPlaceToggle, pickBlockToggle, toolEnabledToggle): they always act.

## Checking a new mod version

Disassemble the mod and malilib (`javap -p -c -constants` per class into `dis/<mod>/<class>.txt`), then
`python3 tools/masa_gates.py <mod dir> <Hotkeys class>`, for example
`python3 tools/masa_gates.py tw fi.dy.masa.tweakeroo.config.Hotkeys`. For each hotkey it prints the callback
branch (every call it makes and the conditions it needs) or the held-key checks (the conditions around them).
`+Option` = must be on; `?call` / `?local` are conditions the script cannot name: read those by hand.

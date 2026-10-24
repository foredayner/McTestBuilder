# Minecraft 1.21.4 port

This is a port of [Minecraft-AI-Build-Assistant](https://github.com/Fyzugx-Lab/Minecraft-AI-Build-Assistant)
(by Fyzugx-Lab, released under CC0 1.0) from Minecraft 1.21.11 to **Minecraft 1.21.4 (Fabric)**.
All credit for the mod goes to the original authors; this fork only changes what 1.21.4 needs.

## What changed

| Area | 1.21.11 | 1.21.4 |
| --- | --- | --- |
| Operator check | `getPermissions()` / `PermissionLevel` (new in 1.21.11) | `hasPermissionLevel(2)` |
| Player's world / server | `getEntityWorld()` | `getServerWorld()` / `getServer()` |
| Key binding category | `KeyBinding.Category.MISC` | `"key.categories.misc"` |
| Screen input | `Click`, `KeyInput` (new in 1.21.9) | `mouseClicked(x, y, button)`, `keyPressed(key, scan, mods)` |
| Cycling buttons | `builder(fn, initial)` | `builder(fn).initially(value)` |
| Build | MC 1.21.11, Yarn 1.21.11+build.6, Fabric API 0.141.4 | MC 1.21.4, Yarn 1.21.4+build.8, Fabric API 0.119.4 |

## Install

Put the jar (not the `-sources` one) and Fabric API for 1.21.4 into `mods/` on **both** the Fabric 1.21.4
server and the client of the player who builds. That player needs operator level 2 (or cheats in single player).

## Added in this fork

- **Code review before running** (no AI call): the script must compile, call only real `ai.*`
  functions with a valid argument count, use block names that exist in 1.21.4 and are not forbidden,
  give walls an `x`/`z` direction and keep literal coordinates within 256 blocks. After the dry run,
  fills over vanilla's 32768-block limit are caught too. Problems are sent back with line numbers.
- **Dry run + auto-fix**: the AI's script is first run in a recording mode (nothing is placed).
  If it fails to compile/run or places nothing, the error and script go back to the AI to be fixed
  (Settings > "Auto-fix failed scripts", default 2). A design that passes goes straight into
  보관함 (no approval step); nothing is placed until you take it out.
- **Tabs**: AI 공방 (cleared every time the window opens) / 보관함 / 작업 일지.
  - 보관함: every finished design is saved (`config/ai_builder/history/<id>.json`) with a top and a
    front preview drawn from block map colours (also saved as PNG). Open one and press **손에 받기**:
    - a building comes as a **blueprint paper**: right-click the top of a block and the building's
      front edge, centred, stands on it, facing you;
    - an object comes as an **object item** whose icon is its front picture (any size ×0.5–×5).
      Right-click the top of a block to set it there, centred and facing you; hold attack on it to
      mine it in the time a stone block takes with your tool, and the item drops again
      (not in creative). Works in survival without cheats once you have the item.
  - Objects are modelled with **smooth shapes** (sphere, ellipsoid, cone, capsule, curved tube, rounded
    box) painted into a 1/16-block voxel grid (coarser for big objects to stay within 400 pieces); each
    block's voxels are covered with as few display entities as possible. `ai.mirrorOn()` / `mirrorOff()`
    make both sides at once. The object prompt has a size guide, style rules and three example scripts
    (dry-run by the game tests). Objects are designed in a fixed frame (front toward -Z) and turned on
    placement.
  - Buildings: the prompt asks for real-world scale (rooms 5×5+, 3-4 blocks of headroom, houses 11×11+).
  - **Equipment** (AI 공방 mode 장비): pick a kind (검, 도끼, 곡괭이, 삽, 괭이, 창, 망치, 지팡이), describe it,
    and the AI models it with the same shapes (grip at the origin, pointing up, at most 2.6 long). 손에 받기
    gives an item with iron stats of that kind (damage, mining, durability, enchanting via item tags, axe
    stripping / shovel paths / hoe tilling) that is drawn as the 3D model in the hand and in slots; the stack
    carries the model, so it works anywhere. 보관함 has 건축 / 소품 / 장비 tabs.
  - **Claude (Anthropic)** provider: Messages API with the fixed instructions as a cached system prompt,
    optional web search (`web_search` tool, at most 3 searches per build).
  - Objects are **solid**: their parts are merged into a few boxes that follow the shape (an
    invisible collidable hitbox entity), so you bump into them and can stand on them. Collision is
    computed by each player's game, so players need this mod. Delete key (twice) or the
    "주변 배치" list removes objects for operators.
  - Logs: full log text by day (`config/ai_builder/logs/yyyy-MM-dd.log`); the live panel shows a
    cleaned version (no CR/tab boxes or stack traces).
- **Settings**: scrolls on small windows with Save/Cancel always visible; typed values survive
  re-layout; Esc saves; the preset button shows the model in use; each provider remembers its model.
- **Ollama**: `keep_alive` (Settings > "Keep model loaded", default 30 min) so the model is not
  unloaded after 5 minutes, and `num_ctx` 8192 so the long build prompt is not cut off.

## Fixed in this fork

- Classic `for (int i = 0; i < n; i++)` loops were rejected by the sandbox as "closures" (Groovy
  keeps the loop header as a ClosureListExpression), so many AI scripts failed with `1 error`.
  Closures and lambdas are still blocked; loops now work.

## Tests (run in CI on every push)

- `./gradlew test` - unit tests: plan rotation, linter, block-state rotation, summon commands,
  rebuild validation, record files.
- `./gradlew runGametest` - a real dedicated server: dry run places nothing, build matches the
  script, saved builds turn with the player, undo restores, objects are display entities.
- `./gradlew runClientGametest` (needs a display; CI uses xvfb) - a real client builds a demo house
  and object and takes screenshots of every page; CI pushes them to the `ci-screenshots` branch.

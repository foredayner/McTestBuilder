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
    Worn kinds (투구, 모자, 갑옷, 등 장식) are equipped on the head or chest, give iron (투구, 갑옷) or leather
    (모자, 등 장식) armour, and are drawn on the head or torso bone (Fabric ArmorRenderer), so they move with the
    wearer.
    The order has effect fields (공격 시 / 대기 시, or 착용 시); left empty there is no effect. Effects are
    whitelisted particles sent by the server (hit bursts, steady puffs while held or worn) and looping
    movements of part groups (spin, swing, bob, pulse) plus glow, drawn by the client from game time.
    Hit effects can be shaped and combined (up to three): burst, shockwave, cone, slash, spiral, pillar,
    lightning, and a magic circle drawn with particles plus glowing display pieces that open, turn and vanish.
    One shaped set per attacker plays at a time; faster hits get a plain burst. Also 'expand' (sphere growing
    out) and 'converge' (sphere shrinking in). Effects make no sounds. An aura (ai.aura) draws a see-through,
    breathing shell of light around every piece while held or worn. If an effect field is filled but the
    script makes nothing for it, the script goes back to the AI.
    Equipment prompts are neutral: the AI makes what was asked in the style the request implies (everyday,
    cute, realistic, sci-fi, fantasy...) instead of fantasy armour; kinds only say where it is worn. Masks and
    costume heads may be about 1.5x the head, with eyes, nose and mouth as 3D shapes. Equipment is painted on a
    1/32-block grid (objects stay 1/16; coarser when 400 pieces are not enough). Only when the order asks for
    something big does the AI call `ai.oversize()`: then up to 5 blocks (e.g. a 4-block balloon on a string
    above the head). Big equipment widens the wearer's on-screen check (a client mixin on
    `EntityRenderer.getBoundingBox`) so it does not vanish when the wearer is off screen.
    Attack effects play when the weapon is swung (the client tells the server when the attack key is pressed
    with a charged attack, not at a block; the server limits how often), in front of the player or on what
    they aim at; mobs holding AI weapons still play them on hit.
    Swords have a 공격 모션 button (기본 / 가로베기 / 내려찍기 / 찌르기 / 회전베기): the swing is drawn with that
    motion in first person (HeldItemRenderer mixin) and third person (BipedEntityModel.animateArms mixin);
    damage, reach and speed stay vanilla.
    Bows (활): a BowItem (drawing, power, enchantments) drawn as the AI model with its grip where the vanilla
    bow sprite's grip is. The AI may design the arrow (ai.arrowStart/arrowEnd) and a trail (ai.arrowTrail);
    the bow's effect field is the arrow effect: trail in flight and ai.hitEffect where it lands. Plain arrows
    shot from it are AibArrowEntity (they carry the bow, so clients draw its arrow shape); tipped and spectral
    arrows stay vanilla.
    Hand-held size: normal tools 1-1.6 long; large ones by nature (greatsword, scythe, war hammer, halberd)
    1.8-3.5 long and up to 2.4 wide without asking; ai.oversize() up to 5. The sword kind only decides
    sword stats and the swing: scythes, hammers and clubs can be ordered as swords. Long models grow toward
    their real size in third person, stay short enough to fit the view in first person, are held with both
    hands in third person (2 blocks or longer), and their swing effects grow with their length.
    Effects are colour dots only (no vanilla particles): `ai.effect(when, [shape, color, color2, size, count, dot,
    plane, start/anchor, moves, speed, ticks, every, fill, lines, points])` with when = attack (on the swing;
    bows: where the arrow lands), idle (while held or worn) or trail (bows). Shapes: burst, sphere, circle, ring,
    heart, star, crescent, slash, cross, spiral, bolt, magic_circle, square, triangle, diamond, line, or custom
    points. Moves: forward, back, rise, fall, expand, shrink, spin, orbit, pulse, scatter, gather, wave, reveal,
    fade, flicker. The server only sends "these effects play here" (attack, mob hit, arrow landing); each game
    draws the glowing dots (SparkRenderer) and finds idle effects and arrow trails by itself. Old magic-circle
    display pieces left in a world are removed on load.
    Sword attack motions run on their own 12-tick clock in four phases (wind up, strike, hold, recover);
    third person moves the body and the other arm too (thrust: blade laid sideways, pulled back, then thrust).
    Opening the AI 공방 (O) empties the effect fields.
  - Objects: the prompt asks the AI to list the real parts and sizes first and to build real things with
    depth on every side (frames, recessed and raised parts, closed backs); a table radio example; objects
    use the 1/32 grid when 400 pieces allow.
  - Web search: the request gets a note to look up the thing's real look (the exact version for a
    character or series), list the features found as comments, and build every one of them.
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

## API for other mods (com.example.api.AiBuildApi, version 1)

Used by the scenario map builder (a separate mod that needs this one):
- `ask(server, prompt, model)`: any question to the configured provider (another model of it if given).
- `designBuilding(world, near, site, request, log)`: a building for a site (width along the front, depth,
  height; front on z = -1, facing +Z), with town style rules, rooms and named spots the script marks with
  `ai.mark(name, x, y, z)`; checked (site bounds, marks) and retried like the mod's own requests.
- `designObject(world, near, description, maxSize, model, log)`: a display-piece object of at most a size.
- `placeBuilding(world, plan, siteMin, site, front, tag, lane, onDone)`: placed as the server, turned so its
  front faces north/east/south/west, footprint starting at the site corner; returns the marked spots in
  world positions. `placeObject(world, plan, onTop, front, tag, lane)`, `removeTagged`, `snapshot`/`restore`.

## Multiplayer

- **Operators only**: the AI 공방 (O key), the Delete-key object removal, `/aibuild`, settings
  sync (provider, model, API key), 손에 받기, undo, approve/cancel and the nearby list need
  permission level 2. Others see "관리자(OP)만 사용할 수 있어요." and the server checks again, so
  a modified client cannot get around it. The API key lives only in the server's config.
  Everyone can still use what was made: place object items, wear and swing gear, mine objects.
  Singleplayer: worlds need cheats on (as before for /fill and undo).
- **Builds at the same time**: each player's placement has its own lane in the queue (one step
  each per turn), every queued command remembers who started it and in which world, undo stops
  only your own build, and "배치 완료" goes to the right person.

## Fixed in this fork

- Claude 5 models think by default, and thinking counts against `max_tokens`: big buildings used
  all 16,000 tokens on thinking and came back with no script. Requests to Claude 5+ models now ask
  for `output_config.effort: medium` with room for 32,000 tokens (and wait up to 10 minutes); an
  answer that still runs out of room is asked again for a shorter script, and its tokens are counted.
  Haiku 5 (cheap and quick) gets 64,000 tokens so big buildings fit.
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

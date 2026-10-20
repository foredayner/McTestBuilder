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

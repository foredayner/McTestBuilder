package com.example.ai.script;

import com.example.ai.api.MinecraftAI;

public final class AiScriptFacade {

    public void placeBlock(Number x, Number y, Number z, String blockName) {
        MinecraftAI.placeBlock(x, y, z, blockName);
    }

    public void placeBlock(String blockName, Number x, Number y, Number z) {
        MinecraftAI.placeBlock(blockName, x, y, z);
    }

    public void placeBox(Number x, Number y, Number z, Number w, Number h, Number l, String blockName) {
        MinecraftAI.placeBox(x, y, z, w, h, l, blockName);
    }

    public void placeBox(String blockName, Number x, Number y, Number z, Number w, Number h, Number l) {
        MinecraftAI.placeBox(blockName, x, y, z, w, h, l);
    }

    public void placePillar(Number x, Number y, Number z, Number h, String blockName) {
        MinecraftAI.placePillar(x, y, z, h, blockName);
    }

    public void placePillar(String blockName, Number x, Number y, Number z, Number h) {
        MinecraftAI.placePillar(blockName, x, y, z, h);
    }

    public void placeFloor(Number x, Number y, Number z, Number w, Number l, String blockName) {
        MinecraftAI.placeFloor(x, y, z, w, l, blockName);
    }

    public void placeFloor(String blockName, Number x, Number y, Number z, Number w, Number l) {
        MinecraftAI.placeFloor(blockName, x, y, z, w, l);
    }

    public void placeFloor(Number x, Number y, Number z, Number w, Number l) {
        MinecraftAI.placeFloor(x, y, z, w, l);
    }

    public void placeRoof(Number x, Number y, Number z, Number w, Number l, String blockName) {
        MinecraftAI.placeRoof(x, y, z, w, l, blockName);
    }

    public void placeRoof(String blockName, Number x, Number y, Number z, Number w, Number l) {
        MinecraftAI.placeRoof(blockName, x, y, z, w, l);
    }

    public void placeRoof(Number x, Number y, Number z, Number w, Number l) {
        MinecraftAI.placeRoof(x, y, z, w, l);
    }

    public void placeWall(Number x, Number y, Number z, Number h, Number l, String first, String second) {
        MinecraftAI.placeWall(x, y, z, h, l, first, second);
    }

    public void placeWall(String blockName, Number x, Number y, Number z, Number h, Number l, String direction) {
        MinecraftAI.placeWall(blockName, x, y, z, h, l, direction);
    }

    public void clearArea(Number x, Number y, Number z, Number w, Number l) {
        MinecraftAI.clearArea(x, y, z, w, l);
    }

    public void clearArea(Number x, Number y, Number z, Number w, Number h, Number l) {
        MinecraftAI.clearArea(x, y, z, w, h, l);
    }

    public String getFacing() {
        return MinecraftAI.getFacing();
    }

    public int getForwardX(Number distance) {
        return MinecraftAI.getForwardX(distance);
    }

    public int getForwardZ(Number distance) {
        return MinecraftAI.getForwardZ(distance);
    }

    public int getGroundLevel(Number x, Number z) {
        return MinecraftAI.getGroundLevel(x, z);
    }

    public String getBlock(Number x, Number y, Number z) {
        return MinecraftAI.getBlock(x, y, z);
    }

    public boolean canPlace(Number x, Number y, Number z) {
        return MinecraftAI.canPlace(x, y, z);
    }

    public boolean isSolid(Number x, Number y, Number z) {
        return MinecraftAI.isSolid(x, y, z);
    }

    public String inspectColumn(Number x, Number z) {
        return MinecraftAI.inspectColumn(x, z);
    }

    public String scanForward(Number maxDistance) {
        return MinecraftAI.scanForward(maxDistance);
    }

    public String scanArea(Number x, Number z, Number width, Number length) {
        return MinecraftAI.scanArea(x, z, width, length);
    }

    public String listBlocks() {
        return MinecraftAI.listBlocks();
    }

    public String listBlocks(Number offset, Number limit) {
        return MinecraftAI.listBlocks(offset, limit);
    }

    public String searchBlocks(String query) {
        return MinecraftAI.searchBlocks(query);
    }

    public String searchBlocks(String query, Number limit) {
        return MinecraftAI.searchBlocks(query, limit);
    }

    public int getBlockListCount() {
        return MinecraftAI.getBlockListCount();
    }

    public boolean isBlockAllowed(String blockName) {
        return MinecraftAI.isBlockAllowed(blockName);
    }

    public void addPart(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz) {
        MinecraftAI.addPart(blockName, x, y, z, sx, sy, sz);
    }

    public void addPart(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz, Number yaw) {
        MinecraftAI.addPart(blockName, x, y, z, sx, sy, sz, yaw);
    }

    public void addPart(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz,
                        Number yaw, Number pitch, Number roll) {
        MinecraftAI.addPart(blockName, x, y, z, sx, sy, sz, yaw, pitch, roll);
    }

    public void addCylinder(String blockName, Number cx, Number y, Number cz, Number radius, Number height) {
        MinecraftAI.addCylinder(blockName, cx, y, cz, radius, height);
    }

    public void addSphere(String blockName, Number cx, Number cy, Number cz, Number radius) {
        MinecraftAI.addSphere(blockName, cx, cy, cz, radius);
    }

    public void addEllipsoid(String blockName, Number cx, Number cy, Number cz, Number rx, Number ry, Number rz) {
        MinecraftAI.addEllipsoid(blockName, cx, cy, cz, rx, ry, rz);
    }

    public void addCone(String blockName, Number cx, Number y, Number cz, Number radius, Number height) {
        MinecraftAI.addCone(blockName, cx, y, cz, radius, height);
    }

    public void addCone(String blockName, Number cx, Number y, Number cz, Number bottomRadius, Number topRadius, Number height) {
        MinecraftAI.addCone(blockName, cx, y, cz, bottomRadius, topRadius, height);
    }

    public void addCapsule(String blockName, Number x1, Number y1, Number z1, Number x2, Number y2, Number z2, Number radius) {
        MinecraftAI.addCapsule(blockName, x1, y1, z1, x2, y2, z2, radius);
    }

    public void addCapsule(String blockName, Number x1, Number y1, Number z1, Number x2, Number y2, Number z2, Number r1, Number r2) {
        MinecraftAI.addCapsule(blockName, x1, y1, z1, x2, y2, z2, r1, r2);
    }

    public void addTube(String blockName, java.util.List<?> points, Number radius) {
        MinecraftAI.addTube(blockName, points, radius);
    }

    public void addTube(String blockName, java.util.List<?> points, Number r1, Number r2) {
        MinecraftAI.addTube(blockName, points, r1, r2);
    }

    public void addRoundBox(String blockName, Number x, Number y, Number z, Number sx, Number sy, Number sz, Number radius) {
        MinecraftAI.addRoundBox(blockName, x, y, z, sx, sy, sz, radius);
    }

    public void mirrorOn() {
        MinecraftAI.mirrorOn();
    }

    public void mirrorOn(Number centerX) {
        MinecraftAI.mirrorOn(centerX);
    }

    public void mirrorOff() {
        MinecraftAI.mirrorOff();
    }

    public void groupStart(String name) {
        MinecraftAI.groupStart(name);
    }

    public void groupEnd() {
        MinecraftAI.groupEnd();
    }

    public void animate(String group, String type, Number px, Number py, Number pz, String axis, Number speed, Number amount) {
        MinecraftAI.animate(group, type, px, py, pz, axis, speed, amount);
    }

    public void glow(String group) {
        MinecraftAI.glow(group);
    }

    public void aura(String color, Number strength) {
        MinecraftAI.aura(color, strength);
    }

    /** A colour-dot effect: ai.effect('attack', [shape: 'heart', color: '#ff66cc', moves: ['forward', 'fade']]). */
    public void effect(String when, java.util.Map<?, ?> settings) {
        MinecraftAI.effect(when, settings);
    }

    public void mark(String name, Number x, Number y, Number z) {
        MinecraftAI.mark(name, x, y, z);
    }

    public void oversize() {
        MinecraftAI.oversize();
    }

    public void arrowStart() {
        MinecraftAI.arrowStart();
    }

    public void arrowEnd() {
        MinecraftAI.arrowEnd();
    }

}

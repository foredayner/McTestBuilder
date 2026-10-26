package com.example.ai.script;

import java.util.List;

public final class AiScriptApi {

    public static final List<String> METHOD_NAMES = List.of(
        "placeBlock",
        "placeBox",
        "placePillar",
        "placeFloor",
        "placeRoof",
        "placeWall",
        "clearArea",
        "getFacing",
        "getForwardX",
        "getForwardZ",
        "getGroundLevel",
        "getBlock",
        "canPlace",
        "isSolid",
        "inspectColumn",
        "scanForward",
        "scanArea",
        "searchBlocks",
        "listBlocks",
        "getBlockListCount",
        "isBlockAllowed",
        "addPart",
        "addCylinder",
        "addSphere",
        "addEllipsoid",
        "addCone",
        "addCapsule",
        "addTube",
        "addRoundBox",
        "mirrorOn",
        "mirrorOff",
        "groupStart",
        "groupEnd",
        "animate",
        "glow",
        "aura",
        "oversize",
        "mark",
        "arrowStart",
        "arrowEnd",
        "effect"
    );

    private AiScriptApi() {
    }
}

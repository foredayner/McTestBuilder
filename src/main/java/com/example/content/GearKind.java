package com.example.content;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Locale;

/**
 * Kinds of hand-held equipment the AI can model. Each one works like an iron version of a
 * vanilla tool or weapon (damage, speed, mining, durability, enchantments) and looks like the
 * AI's model.
 */
public enum GearKind {
    SWORD("검", Items.IRON_SWORD, Items.IRON_SWORD,
        "a grip (0.25-0.35 long) around the hand, a cross guard just above it, then the blade straight up; total 1.0-1.6 "
            + "(a greatsword up to 2.2). Blade 0.12-0.3 wide (X) and 0.04-0.08 thick (Z), ending in a point."),
    AXE("도끼", Items.IRON_AXE, Items.IRON_AXE,
        "a handle 0.9-1.3 long going up; near the top a head whose blade sticks out to +X (0.25-0.5 wide), "
            + "thin in Z (0.04-0.1), with a curved cutting edge."),
    PICKAXE("곡괭이", Items.IRON_PICKAXE, Items.IRON_PICKAXE,
        "a handle 0.9-1.2 long going up; at the top a head across X in both directions (0.6-0.9 wide), "
            + "its two points curving slightly down."),
    SHOVEL("삽", Items.IRON_SHOVEL, Items.IRON_SHOVEL,
        "a handle 0.9-1.2 long going up; at the top a flat rounded blade (0.25-0.35 wide in X, thin in Z), "
            + "pointing up."),
    HOE("괭이", Items.IRON_HOE, Items.IRON_HOE,
        "a handle 0.9-1.2 long going up; at the top a small flat blade sticking out to +X and bent down."),
    SPEAR("창", Items.IRON_SWORD, Items.TRIDENT,
        "a long thin shaft (1.6-2.4) going up, a pointed head (0.25-0.45 long) on top, maybe a binding or tassel "
            + "under the head."),
    HAMMER("망치", Items.IRON_AXE, Items.MACE,
        "a handle 0.9-1.4 long going up; at the top a heavy head across X (0.4-0.7 wide, 0.2-0.35 tall and deep)."),
    STAFF("지팡이", Items.IRON_SWORD, Items.BLAZE_ROD,
        "a shaft (1.2-2.0) going up, with an ornament on top: an orb, crystal, crescent or carved head; "
            + "rings or wraps along the shaft."),

    // Worn equipment: drawn on the wearer's head or torso and following its movement.
    HELMET("투구", Items.IRON_HELMET, Items.IRON_HELMET, EquipmentSlot.HEAD,
        "a helmet that encloses the head box (start 0.02-0.06 outside it): a shell, a darker rim, a visor or eye slit "
            + "on the front, maybe horns, a crest or a plume on top."),
    HAT("모자", Items.LEATHER_HELMET, Items.LEATHER_HELMET, EquipmentSlot.HEAD,
        "a hat, crown, hood or ears sitting on or around the top of the head box (from about y = 0.35 up); "
            + "brims stick out around the head; keep the face (front, y 0.05-0.4) free."),
    CHESTPLATE("갑옷", Items.IRON_CHESTPLATE, Items.IRON_CHESTPLATE, EquipmentSlot.CHEST,
        "armour around the torso box (0.02-0.06 outside it): breastplate on the front, back plate, a belt at the "
            + "bottom, shoulder pads at the top corners (they may stick out to x = +-0.4), emblems and rivets."),
    BACK("등 장식", Items.LEATHER_CHESTPLATE, Items.ELYTRA, EquipmentSlot.CHEST,
        "something worn on the back (z from 0.125 outward): wings (may spread to x = +-1.2), a backpack, a quiver, "
            + "a sheathed sword, a shell; straps over the front of the torso hold it on.");

    /** Where it is worn (head or chest), or null for hand-held equipment. */
    public final EquipmentSlot slot;

    /** Shown in the UI and the item name. */
    public final String korean;
    /** The vanilla item whose stats and behaviour this kind copies. */
    public final Item base;
    /** Small icon shown on 보관함 entries so you can tell which kind it is. */
    public final Item icon;
    /** Shape guide for the AI. */
    public final String guide;

    GearKind(String korean, Item base, Item icon, String guide) {
        this(korean, base, icon, null, guide);
    }

    GearKind(String korean, Item base, Item icon, EquipmentSlot slot, String guide) {
        this.korean = korean;
        this.base = base;
        this.icon = icon;
        this.slot = slot;
        this.guide = guide;
    }

    public boolean worn() {
        return slot != null;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GearKind of(String id) {
        if (id != null) {
            for (GearKind kind : values()) {
                if (kind.id().equalsIgnoreCase(id.trim())) {
                    return kind;
                }
            }
        }
        return SWORD;
    }
}

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
        "anything swung to hit: a sword, katana, greatsword, scythe, sickle, hammer, club, bat, stick, toy sword, "
            + "fish... (this kind only decides sword stats and the swing; the shape is whatever the request says). "
            + "Sword: a grip (0.25-0.35, two-handed 0.4-0.7) around the hand, a guard above it if it has one, then the "
            + "blade straight up, 0.12-0.3 wide (X; a greatsword 0.3-0.6) and 0.04-0.1 thick (Z); 1.0-1.6 long, a "
            + "greatsword 1.8-3.2. Scythe: a long shaft (1.6-2.8) up from the hand with a big curved blade at the top "
            + "sweeping out to one side (+X or -X, 0.8-1.6 long) and curving down. Hammer or club: a shaft with a heavy "
            + "head at the top (a big hammer head 0.6-1.2 wide)."),
    AXE("도끼", Items.IRON_AXE, Items.IRON_AXE,
        "a handle 0.9-1.3 long going up (a great axe 1.6-2.8); near the top a head whose blade sticks out to +X "
            + "(0.25-0.5 wide, a great axe up to 1.2), thin in Z (0.04-0.1), with a curved cutting edge."),
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
        "a handle 0.9-1.4 long going up (a war hammer 1.6-2.8); at the top a heavy head across X (0.4-0.7 wide, a "
            + "war hammer 0.8-1.6; 0.2-0.6 tall and deep)."),
    STAFF("지팡이", Items.IRON_SWORD, Items.BLAZE_ROD,
        "a long held thing (a staff, wand, walking stick, umbrella, flag, microphone stand...): a shaft (1.2-2.0) going "
            + "up with whatever sits on top."),

    BOW("활", Items.BOW, Items.BOW,
        "a bow held at the middle of its grip at (0, 0, 0): the limbs go up and down along Y (tips at about y = +-0.55 to "
            + "+-0.7) and bend toward +X at the tips (x about 0.2-0.35); the string runs between the tips on the +X side; "
            + "arrows fly toward -X. Keep it thin in Z (0.04-0.1). You may also design the arrow it shoots between "
            + "ai.arrowStart() and ai.arrowEnd(): the arrow points UP (+Y), fletching at y = 0 to 0.2, shaft up to the head "
            + "at y = 0.6-0.9, centred on x = z = 0, about 0.03-0.08 thick (without it, arrows look normal)."),

    // Worn equipment: drawn on the wearer's head or torso and following its movement.
    HELMET("투구", Items.IRON_HELMET, Items.IRON_HELMET, EquipmentSlot.HEAD,
        "anything that covers the whole head: a helmet, a mask, a mascot, animal or character head, a diving helmet, "
            + "a box or pumpkin head... Fitted ones start 0.02-0.06 outside the head box; masks and costume heads may be "
            + "bigger, with eyes, nose and mouth as 3D shapes on the front."),
    HAT("모자", Items.LEATHER_HELMET, Items.LEATHER_HELMET, EquipmentSlot.HEAD,
        "something on or around the top of the head that leaves the face (front, y 0.05-0.4) free: hats, caps, "
            + "crowns, hoods, ears, hair, headphones, glasses pushed up, a halo; mostly from about y = 0.35 up."),
    CHESTPLATE("갑옷", Items.IRON_CHESTPLATE, Items.IRON_CHESTPLATE, EquipmentSlot.CHEST,
        "something worn on the torso: clothes, a jacket, a vest, a costume body, armour, a necklace, a scarf; "
            + "0.02-0.06 outside the torso box, shoulder parts may stick out to x = +-0.4."),
    BACK("등 장식", Items.LEATHER_CHESTPLATE, Items.ELYTRA, EquipmentSlot.CHEST,
        "something on the back or behind or above the wearer: wings, a backpack, a cape, a shell, a tail, a balloon "
            + "on a string; carried things are held on by straps over the front of the torso.");

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

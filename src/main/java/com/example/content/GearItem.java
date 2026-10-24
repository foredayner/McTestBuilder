package com.example.content;

/**
 * An AI-modelled piece of equipment: a weapon or tool ({@link GearToolItem}), a bow
 * ({@link GearBowItem}) or something worn. The look comes from the model stored on each stack
 * (drawn by the client); the behaviour from the vanilla item of its kind.
 */
public interface GearItem {

    GearKind kind();
}

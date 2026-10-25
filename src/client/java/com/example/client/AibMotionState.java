package com.example.client;

/** Added to biped render states: the attack motion of the main-hand AI sword ("" = none). */
public interface AibMotionState {

    String aib$getMotion();

    void aib$setMotion(String motion);

    /** A long AI weapon in the main hand: held with both hands. */
    boolean aib$isTwoHanded();

    void aib$setTwoHanded(boolean twoHanded);
}

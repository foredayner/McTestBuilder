package com.example.mixin.client;

import com.example.client.AibMotionState;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BipedEntityRenderState.class)
public abstract class BipedEntityRenderStateMixin implements AibMotionState {

    @Unique
    private String aib$motion = "";

    @Unique
    private boolean aib$twoHanded;

    @Override
    public boolean aib$isTwoHanded() {
        return aib$twoHanded;
    }

    @Override
    public void aib$setTwoHanded(boolean twoHanded) {
        aib$twoHanded = twoHanded;
    }

    @Override
    public String aib$getMotion() {
        return aib$motion;
    }

    @Override
    public void aib$setMotion(String motion) {
        aib$motion = motion == null ? "" : motion;
    }
}

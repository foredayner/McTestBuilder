package com.example.client;

import com.example.content.AibContent;
import com.example.content.GearKind;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws worn AI equipment (helmets, hats, armour, back items) on players, zombies, armour stands
 * and other biped models, attached to the head or the torso so it follows their movement.
 *
 * <p>The AI designs with +Y up; entity models have +Y down, so the model is flipped on Y (which
 * also turns its faces inside out, hence a no-cull layer).
 */
public final class WearRenderer implements ArmorRenderer {

    private WearRenderer() {
    }

    public static void register() {
        List<Item> worn = new ArrayList<>();
        for (GearKind kind : GearKind.values()) {
            if (kind.worn()) {
                worn.add(AibContent.GEAR.get(kind));
            }
        }
        ArmorRenderer.register(new WearRenderer(), worn.toArray(new Item[0]));
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, BipedEntityRenderState state,
                       EquipmentSlot slot, int light, BipedEntityModel<BipedEntityRenderState> contextModel) {
        if (!(stack.getItem() instanceof com.example.content.GearItem gear) || gear.kind().slot != slot) {
            return;
        }
        GearModelRenderer.Model model = GearModelRenderer.modelFor(stack);
        if (model == null || model.parts().isEmpty()) {
            return;
        }
        ModelPart bone = slot == EquipmentSlot.HEAD ? contextModel.head : contextModel.body;
        if (!bone.visible) {
            return;
        }
        matrices.push();
        bone.rotate(matrices);
        matrices.scale(1.0F, -1.0F, 1.0F);
        GearModelRenderer.draw(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)),
            model, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}

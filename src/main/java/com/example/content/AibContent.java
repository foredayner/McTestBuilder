package com.example.content;

import com.example.AiBuildAssistantMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Things this mod adds to the game: the solid object hitbox and the object item. */
public final class AibContent {

    public static final Identifier HITBOX_ID = Identifier.of(AiBuildAssistantMod.MOD_ID, "hitbox");
    public static final Identifier OBJECT_ID = Identifier.of(AiBuildAssistantMod.MOD_ID, "object");

    public static EntityType<SolidHitboxEntity> HITBOX;
    public static Item OBJECT_ITEM;
    /** One item per equipment kind (so tags like #minecraft:swords and enchantments work). */
    public static final java.util.Map<GearKind, Item> GEAR = new java.util.EnumMap<>(GearKind.class);

    private AibContent() {
    }

    private static <T> void copy(Item.Settings settings, Item from, net.minecraft.component.ComponentType<T> type) {
        T value = from.getComponents().get(type);
        if (value != null) {
            settings.component(type, value);
        }
    }

    public static void register() {
        RegistryKey<EntityType<?>> entityKey = RegistryKey.of(RegistryKeys.ENTITY_TYPE, HITBOX_ID);
        HITBOX = Registry.register(Registries.ENTITY_TYPE, entityKey,
            EntityType.Builder.<SolidHitboxEntity>create(SolidHitboxEntity::new, SpawnGroup.MISC)
                .dimensions(0.0F, 0.0F)
                .maxTrackingRange(10)
                .build(entityKey));

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, OBJECT_ID);
        OBJECT_ITEM = Registry.register(Registries.ITEM, itemKey, new ObjectItem(new Item.Settings().registryKey(itemKey)));

        for (GearKind kind : GearKind.values()) {
            RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(AiBuildAssistantMod.MOD_ID, "gear_" + kind.id()));
            Item.Settings settings = new Item.Settings().registryKey(key).maxCount(1);
            copy(settings, kind.base, net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS);
            copy(settings, kind.base, net.minecraft.component.DataComponentTypes.TOOL);
            copy(settings, kind.base, net.minecraft.component.DataComponentTypes.MAX_DAMAGE);
            copy(settings, kind.base, net.minecraft.component.DataComponentTypes.DAMAGE);
            copy(settings, kind.base, net.minecraft.component.DataComponentTypes.ENCHANTABLE);
            copy(settings, kind.base, net.minecraft.component.DataComponentTypes.REPAIRABLE);
            GEAR.put(kind, Registry.register(Registries.ITEM, key, new GearItem(kind, settings)));
        }
    }
}

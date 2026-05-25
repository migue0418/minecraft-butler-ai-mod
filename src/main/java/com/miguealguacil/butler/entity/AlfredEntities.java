package com.miguealguacil.butler.entity;

import com.miguealguacil.butler.AIButler;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;

public final class AlfredEntities {

    private static final ResourceKey<EntityType<?>> ALFRED_KEY = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath(AIButler.MOD_ID, "alfred")
    );

    public static final EntityType<AlfredEntity> ALFRED = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ALFRED_KEY,
        EntityType.Builder.<AlfredEntity>of(AlfredEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.95f)
            .build(ALFRED_KEY)
    );

    private AlfredEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(ALFRED, PathfinderMob.createMobAttributes().build());
    }
}

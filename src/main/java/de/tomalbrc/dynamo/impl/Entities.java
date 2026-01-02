package de.tomalbrc.dynamo.impl;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;

public class Entities {
    public static final EntityType<@NotNull CarEntity> CAR = register(CarEntity.ID, FabricEntityType.Builder.createLiving(CarEntity::new, MobCategory.MISC, x -> x.defaultAttributes(Animal::createAnimalAttributes)).noSummon().sized(2, 1.5f));

    private static <T extends Entity> EntityType<@NotNull T> register(Identifier id, EntityType.Builder<@NotNull T> builder) {
        EntityType<@NotNull T> type = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        PolymerEntityUtils.registerType(type);

        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
    }

    public static void init(){}
}

package de.tomalbrc.dynamo.impl.entity;

import com.google.common.collect.Maps;
import de.tomalbrc.dynamo.impl.config.VehicleConfigLoader;
import de.tomalbrc.dynamo.impl.config.vehicle.VehicleConfig;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class Entities {
    public static final Map<Identifier, EntityType<@NotNull VehicleEntity>> ENTITY_TYPES = Maps.newHashMap();

    private static <T extends VehicleEntity> EntityType<@NotNull T> register(Identifier id, EntityType.Builder<@NotNull T> builder) {
        EntityType<@NotNull T> type = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        PolymerEntityUtils.registerType(type);

        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
    }

    public VehicleEntity create(Identifier id, ServerLevel level) {
        return ENTITY_TYPES.get(id).create(level, EntitySpawnReason.COMMAND);
    }

    public static void init() {
        for (Map.Entry<Identifier, VehicleConfig> entry : VehicleConfigLoader.getAll().entrySet()) {
            var identifier = entry.getKey();
            EntityType<@NotNull VehicleEntity> e = register(identifier, EntityType.Builder.<VehicleEntity>of((l, p) -> new VehicleEntity(l, p, VehicleConfigLoader.get(identifier)), MobCategory.MISC).sized(entry.getValue().halfWidth*2f, entry.getValue().halfHeight*2f));
            ENTITY_TYPES.put(identifier, e);
        }
    }
}

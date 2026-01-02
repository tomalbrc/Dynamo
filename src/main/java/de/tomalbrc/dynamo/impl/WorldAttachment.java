package de.tomalbrc.dynamo.impl;

import de.tomalbrc.dynamo.Dynamo;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldAttachment implements HolderAttachment {
    protected final ServerLevel level;
    protected final ElementHolder holder;

    private Vec3 position;

    private static final Map<Level, List<WorldAttachment>> ATTACHMENTS = new ConcurrentHashMap<>();

    public static void registerEventHandler() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, serverLevel) -> {
            if (entity.getType() == EntityType.PLAYER) {
                var list = ATTACHMENTS.get(serverLevel);
                if (list != null) {
                    for (WorldAttachment attachment : list) {
                        attachment.updateTracking(((ServerPlayer)entity).connection);
                    }
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, serverLevel) -> {
            if (entity.getType() == EntityType.PLAYER) {
                var list = ATTACHMENTS.get(serverLevel);
                if (list != null) {
                    for (WorldAttachment attachment : list) {
                        attachment.updateTracking(((ServerPlayer)entity).connection);
                    }
                }
            }
        });

        ServerTickEvents.START_WORLD_TICK.register(serverLevel -> {
            var list = ATTACHMENTS.get(serverLevel);
            if (list != null) {
                list.removeIf(x -> x.getWorld() == null || Dynamo.SERVER.getLevel(serverLevel.dimension()) == null);
                list.forEach(HolderAttachment::tick);
            }
        });
    }

    public WorldAttachment(ServerLevel level, ElementHolder holder, Vec3 pos) {
        this.position = pos;
        this.level = level;
        this.holder = holder;
        this.holder.setAttachment(this);

        ATTACHMENTS.computeIfAbsent(level, (k) -> Collections.synchronizedList(new ObjectArrayList<>())).add(this);

        updateCurrentlyTracking(level.players().stream().map(x -> x.connection).toList());
    }

    public static WorldAttachment of(ServerLevel level, ElementHolder holder, Vec3 pos) {
        return new WorldAttachment(level, holder, pos);
    }

    public void setPos(Vec3 position) {
        this.position = position;
    }

    @Override
    public ElementHolder holder() {
        return holder;
    }

    @Override
    public void destroy() {
        var list = ATTACHMENTS.get(level);
        if (list != null)
            list.remove(this);
    }

    @Override
    public Vec3 getPos() {
        return position;
    }

    @Override
    public ServerLevel getWorld() {
        return level;
    }

    @Override
    public void updateCurrentlyTracking(Collection<ServerGamePacketListenerImpl> currentlyTracking) {
        if (Dynamo.SERVER.getLevel(level.dimension()) == null) {
            for (var x : currentlyTracking) {
                this.holder.stopWatching(x);
            }
            return;
        }

        var watching = level.players().stream().map(x -> x.connection).toList();
        for (var player : currentlyTracking) {
            if (!watching.contains(player)) {
                this.holder.stopWatching(player);
            }
        }

        for (var x : watching) {
            this.holder.startWatching(x.getPlayer().connection);
        }
    }

    @Override
    public void updateTracking(ServerGamePacketListenerImpl tracking) {
        if (tracking.player.isDeadOrDying() || tracking.player.hasDisconnected()) {
            this.stopWatching(tracking);
        }
    }

}

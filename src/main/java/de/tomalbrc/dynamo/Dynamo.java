package de.tomalbrc.dynamo;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.logging.LogUtils;
import de.tomalbrc.dynamo.api.event.ServerEvents;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.NativeLoader;
import de.tomalbrc.dynamo.impl.WorldAttachment;
import de.tomalbrc.dynamo.impl.command.ModCommands;
import de.tomalbrc.dynamo.impl.physics.Shaper;
import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import de.tomalbrc.dynamo.impl.world.DynamicWorldContainer;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Dynamo implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ExecutorService COLLISION_GENERATOR_EXECUTOR = Executors.newWorkStealingPool();
    public static final String MODID = "dynamo";

    public static MinecraftServer SERVER;

    @Override
    public void onInitialize() {
        NativeLoader.load();

        ModCommands.register();
        WorldAttachment.registerEventHandler();

        ServerWorldEvents.LOAD.register((minecraftServer, serverLevel) -> {
            ((DynamicWorldContainer)serverLevel).setDynamicWorld(new DynamicWorld());
        });
        ServerWorldEvents.UNLOAD.register((minecraftServer, serverLevel) -> {
            var world = ((DynamicWorldContainer)serverLevel).getDynamicWorld();
            if (world != null)
                world.close();
        });

        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> SERVER = minecraftServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
            COLLISION_GENERATOR_EXECUTOR.shutdownNow();
        });

        ServerTickEvents.START_WORLD_TICK.register(level -> {
            var world = ((DynamicWorldContainer) level).getDynamicWorld();
            world.tick(level);
        });

        ServerEvents.Block.BLOCK_UPDATE.register((level, pos, blockState, blockPos) -> {
            ((DynamicWorldContainer) level).getDynamicWorld().updateBlock(level, level.getBlockState(pos), pos);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            ((DynamicWorldContainer) level).getDynamicWorld().unloadChunk(level, chunk);
        });
    }

    public static void spawnFor(ServerPlayer player) {
        var level = player.level();
        var world = ((DynamicWorldContainer)level).getDynamicWorld();


        var body = new PhysicsRigidBody(Shaper.shape(Shapes.create(AABB.ofSize(Vec3.ZERO, 1, 1, 1f/16f))), 0.1f);
        body.setFriction(1.f);
        body.setRestitution(0.f);
        var bodyPos = player.position().offsetRandom(player.getRandom(), 1.5f).toVector3f();
        body.setPhysicsTransform(new Transform(new Vector3f(bodyPos.x, bodyPos.y, bodyPos.z), Quaternion.IDENTITY));

        world.getPhysicsThread().enqueue(() -> world.getPhysicsSpace().addCollisionObject(body));

        var holder = new ElementHolder();

        ItemDisplayElement displayElement = new ItemDisplayElement(Items.DIAMOND.getDefaultInstance());
        displayElement.setTeleportDuration(3);
        displayElement.setInterpolationDuration(3);
        world.addElement(new DynamicElement(body, e -> {
            var pos = e.physicsBody().getTransform(null).getTranslation();
            displayElement.setOverridePos(new Vec3(pos.x, pos.y, pos.z));
            var rot = e.physicsBody().getTransform(null).getRotation();
            displayElement.setLeftRotation(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
            displayElement.startInterpolationIfDirty();
        }, e -> {
            holder.destroy();
            world.getPhysicsThread().enqueue(() -> world.getPhysicsSpace().remove(body));
        }));

        holder.addElement(displayElement);
        WorldAttachment.of(level, holder, player.position());
    }
}

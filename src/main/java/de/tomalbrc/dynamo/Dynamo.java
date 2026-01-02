package de.tomalbrc.dynamo;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.logging.LogUtils;
import de.tomalbrc.dynamo.api.event.ServerEvents;
import de.tomalbrc.dynamo.impl.NativeLoader;
import de.tomalbrc.dynamo.impl.WorldAttachment;
import de.tomalbrc.dynamo.impl.command.ModCommands;
import de.tomalbrc.dynamo.impl.world.DynamicWorldContainer;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Dynamo implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();
    public static final String MODID = "dynamo";

    public static MinecraftServer SERVER;


    @Override
    public void onInitialize() {
        NativeLoader.load();

        ModCommands.register();
        WorldAttachment.registerEventHandler();

        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> SERVER = minecraftServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> EXECUTOR.shutdownNow());

        ServerTickEvents.START_WORLD_TICK.register(level -> {
            var world = ((DynamicWorldContainer) level).getDynamicWorld();
            world.tick(level);

            if ((int)(Math.random() * 100) == 1) {
                for (ServerPlayer player : level.players()) {
                    spawnFor(player);
                }
            }
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

        var body = new PhysicsRigidBody(new BoxCollisionShape(0.5f));
        body.setPhysicsTransform(new Transform(new Vector3f((float)player.position().x, (float)player.position().y, (float)player.position().z), Quaternion.IDENTITY));

        ((DynamicWorldContainer) level).getDynamicWorld().getPhysicsSpace().addCollisionObject(body);

        var holder = new ElementHolder();

        ItemDisplayElement displayElement = new ItemDisplayElement(Items.DIRT.getDefaultInstance());
        displayElement.setTeleportDuration(2);
        displayElement.setInterpolationDuration(2);
        world.addElement(new DynamicElement(body, e -> {
            var pos = e.physicsBody().getTransform(null).getTranslation();
            displayElement.setOverridePos(new Vec3(pos.x, pos.y, pos.z));
            var rot = e.physicsBody().getTransform(null).getRotation();
            displayElement.setLeftRotation(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
            displayElement.startInterpolationIfDirty();
        }, e -> {
            holder.destroy();
        }));

        holder.addElement(displayElement);
        WorldAttachment.of(level, holder, player.position());
    }
}

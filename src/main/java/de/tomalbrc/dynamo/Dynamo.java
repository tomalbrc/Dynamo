package de.tomalbrc.dynamo;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsCharacter;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.logging.LogUtils;
import com.simsilica.mathd.Vec3d;
import de.tomalbrc.dynamo.api.event.ServerEvents;
import de.tomalbrc.dynamo.impl.Entities;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
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

        Entities.init();
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

        ServerTickEvents.START_WORLD_TICK.register(level -> {
            var world = ((DynamicWorldContainer) level).getDynamicWorld();
            world.tick(level);

            world.getPhysicsThread().enqueue(space -> {
                for (Map.Entry<ServerPlayer, PhysicsCharacter> entry : characterMap.entrySet()) {
                    entry.getValue().setPhysicsLocationDp(new Vec3d(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ()));
                }
            });
        });

        ServerEvents.Block.BLOCK_UPDATE.register((level, pos, blockState, blockPos) -> {
            ((DynamicWorldContainer) level).getDynamicWorld().updateBlock(level, level.getBlockState(pos), pos);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            ((DynamicWorldContainer) level).getDynamicWorld().unloadChunk(level, chunk);
        });
    }

    static Map<ServerPlayer, PhysicsCharacter> characterMap = new IdentityHashMap<>();

    public static void testBlock(ServerPlayer player) {
        var level = player.level();
        var world = ((DynamicWorldContainer)level).getDynamicWorld();

//        if (!characterMap.containsKey(player)) {
//            PhysicsCharacter character = new PhysicsCharacter(new BoxCollisionShape(0.4f, 0.9f, 0.4f), 0.5f);
//            world.getPhysicsThread().enqueue(space -> space.add(character));
//            characterMap.put(player, character);
//        }

        var body = new PhysicsRigidBody(Shaper.shape(Shapes.block()), 1.0f);
        body.setFriction(2.f);
        body.setRestitution(1.f);
        var bodyPos = player.position().offsetRandom(player.getRandom(), 1.5f).toVector3f();
        body.setPhysicsTransform(new Transform(new Vector3f(bodyPos.x, bodyPos.y, bodyPos.z), Quaternion.IDENTITY));

        world.getPhysicsThread().enqueue(space -> space.addCollisionObject(body));

        var holder = new ElementHolder();

        ItemDisplayElement displayElement = new ItemDisplayElement(Items.STONE.getDefaultInstance());
        displayElement.setTeleportDuration(2);
        displayElement.setInterpolationDuration(2);
        world.addElement(new DynamicElement(body, e -> {
            var pos = e.physicsBody().getTransform(null).getTranslation();
            displayElement.setOverridePos(new Vec3(pos.x, pos.y+0.01, pos.z));
            var rot = e.physicsBody().getTransform(null).getRotation();
            displayElement.setLeftRotation(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
            displayElement.startInterpolationIfDirty();
        }, e -> {
            holder.destroy();
            world.getPhysicsThread().enqueue(space -> space.remove(body));
        }));

        holder.addElement(displayElement);
        WorldAttachment.of(level, holder, player.position());
    }

    public static void testItem(ServerPlayer player) {
        var level = player.level();
        var world = ((DynamicWorldContainer)level).getDynamicWorld();

//        if (!characterMap.containsKey(player)) {
//            PhysicsCharacter character = new PhysicsCharacter(new BoxCollisionShape(0.4f, 0.9f, 0.4f), 0.5f);
//            world.getPhysicsThread().enqueue(space -> space.add(character));
//            characterMap.put(player, character);
//        }

        var body = new PhysicsRigidBody(Shaper.shape(Shapes.create(AABB.ofSize(Vec3.ZERO, 1, 1, 1f/16f))), 0.1f);
        body.setFriction(2.f);
        body.setRestitution(0.f);
        var bodyPos = player.position().offsetRandom(player.getRandom(), 1.5f).toVector3f();
        body.setPhysicsTransform(new Transform(new Vector3f(bodyPos.x, bodyPos.y, bodyPos.z), Quaternion.IDENTITY));

        world.getPhysicsThread().enqueue(space -> space.addCollisionObject(body));

        var holder = new ElementHolder();

        ItemDisplayElement displayElement = new ItemDisplayElement(Items.DIAMOND.getDefaultInstance());
        displayElement.setTeleportDuration(3);
        displayElement.setInterpolationDuration(3);
        world.addElement(new DynamicElement(body, e -> {
            var pos = e.physicsBody().getTransform(null).getTranslation();
            displayElement.setOverridePos(new Vec3(pos.x, pos.y+0.01, pos.z));
            var rot = e.physicsBody().getTransform(null).getRotation();
            displayElement.setLeftRotation(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
            displayElement.startInterpolationIfDirty();
        }, e -> {
            holder.destroy();
            world.getPhysicsThread().enqueue(space -> space.remove(body));
        }));

        holder.addElement(displayElement);
        WorldAttachment.of(level, holder, player.position());
    }
}

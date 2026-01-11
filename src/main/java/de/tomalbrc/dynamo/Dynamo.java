package de.tomalbrc.dynamo;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.mojang.logging.LogUtils;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.dynamo.api.event.BlockEvents;
import de.tomalbrc.dynamo.impl.entity.Entities;
import de.tomalbrc.dynamo.impl.command.ModCommands;
import de.tomalbrc.dynamo.impl.mesh.Shaper;
import de.tomalbrc.dynamo.impl.model.Loader;
import de.tomalbrc.dynamo.impl.model.Models;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.util.NativeLoader;
import de.tomalbrc.dynamo.impl.util.WorldAttachment;
import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import de.tomalbrc.dynamo.impl.world.DynamicWorldContainer;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Dynamo implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ExecutorService COLLISION_GEN = Executors.newVirtualThreadPerTaskExecutor();
    public static final ExecutorService PHYSICS = Executors.newSingleThreadExecutor();
    public static final String MODID = "dynamo";

    public static MinecraftServer SERVER;

    public static Model model;

    @Override
    public void onInitialize() {
        PolymerResourcePackUtils.addModAssets(MODID);

        Loader.load("car112").ifPresent(model -> Models.put("car112", model));

        NativeLoader.load();
        initJolt();

        Entities.init();
        ModCommands.register();
        WorldAttachment.registerEventHandler();

        ServerWorldEvents.LOAD.register((minecraftServer, serverLevel) -> {
            ((DynamicWorldContainer)serverLevel).setDynamicWorld(new DynamicWorld(serverLevel));
        });
        ServerWorldEvents.UNLOAD.register((minecraftServer, serverLevel) -> {
            var world = ((DynamicWorldContainer)serverLevel).getDynamicWorld();
            if (world != null)
                world.close();
        });

        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> SERVER = minecraftServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(minecraftServer -> {
            COLLISION_GEN.shutdownNow();
            PHYSICS.shutdownNow();
        });

        ServerTickEvents.START_WORLD_TICK.register(level -> {
            var world = ((DynamicWorldContainer) level).getDynamicWorld();
            world.tick(level);

//            world.getPhysicsThread().enqueue(space -> {
//                for (Map.Entry<ServerPlayer, PhysicsCharacter> entry : characterMap.entrySet()) {
//                    entry.getValue().setPhysicsLocationDp(new Vec3d(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ()));
//                }
//            });
        });

        
        BlockEvents.Block.BLOCK_UPDATE.register((level, pos, blockState, blockPos) -> {
            ((DynamicWorldContainer) level).getDynamicWorld().updateBlock(level, level.getBlockState(pos), pos);
        });

        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            ((DynamicWorldContainer) level).getDynamicWorld().loadChunk(level, chunk);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            ((DynamicWorldContainer) level).getDynamicWorld().unloadChunk(level, chunk);
        });
    }

    private static void initJolt() {
        JoltPhysicsObject.startCleaner(); // to free Jolt-Physics objects automatically
        Jolt.registerDefaultAllocator(); // tell Jolt Physics to use malloc/free

        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();

        boolean success = Jolt.newFactory();
        assert success;
        Jolt.registerTypes();
    }

    public static void testBlock(ServerPlayer player) {
        var level = player.level();
        var world = ((DynamicWorldContainer)level).getDynamicWorld();

        var bodyPos = player.position().offsetRandom(player.getRandom(), 1.5f);

        var shapeSettings = Shaper.shape(Shapes.block());
        var bodySettings = new BodyCreationSettings()
                .setShapeSettings(shapeSettings)
                .setMotionType(EMotionType.Dynamic)
                .setObjectLayer(DynamicWorld.objLayerMoving)
                .setFriction(1.0f)
                .setRestitution(0.1f)
                .setPosition(new RVec3(bodyPos.x, bodyPos.y, bodyPos.z));

        bodySettings.setMassPropertiesOverride(new MassProperties());
        bodySettings.getMassPropertiesOverride().setMass(0.01f);

        var bi = world.getPhysicsSystem().getBodyInterface();
        var body = bi.createBody(bodySettings);
        bi.addBody(body.getId(), EActivation.Activate);

        var holder = new ElementHolder();

        var displayElement = new BlockDisplayElement(Blocks.STONE.defaultBlockState());
        //displayElement.setTranslation(new Vector3f(.5f));
        displayElement.setTeleportDuration(2);
        displayElement.setInterpolationDuration(2);
        world.addElement(new DynamicElement(body.getId(), e -> {
            RVec3 pos = new RVec3();
            Quat rot = new Quat();
            bi.getPositionAndRotation(e.physicsBody(), pos, rot);
            displayElement.setOverridePos(new Vec3(pos.x(), pos.y()+0.01, pos.z()));
            displayElement.setLeftRotation(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
            displayElement.startInterpolationIfDirty();
        }, e -> {
            holder.destroy();
            bi.removeBody(e.physicsBody());
        }));

        holder.addElement(displayElement);
        WorldAttachment.of(level, holder, player.position());
    }

    public static void testItem(ServerPlayer player) {
        var level = player.level();
        var world = ((DynamicWorldContainer)level).getDynamicWorld();

        var bodyPos = player.position().offsetRandom(player.getRandom(), 1.5f);

        var shapeSettings = Shaper.shape(Shapes.create(AABB.ofSize(Vec3.ZERO, 1, 1, 1f/16f)));
        var bodySettings = new BodyCreationSettings()
                .setShapeSettings(shapeSettings)
                .setMotionType(EMotionType.Dynamic)
                .setObjectLayer(DynamicWorld.objLayerMoving)
                .setFriction(1.0f)
                .setRestitution(0.4f)
                .setPosition(new RVec3(bodyPos.x, bodyPos.y, bodyPos.z));

        bodySettings.setMassPropertiesOverride(new MassProperties());
        bodySettings.getMassPropertiesOverride().setMass(0.001f);

        var bi = world.getPhysicsSystem().getBodyInterface();
        var body = bi.createBody(bodySettings);
        bi.addBody(body.getId(), EActivation.Activate);

        var holder = new ElementHolder();

        ItemDisplayElement displayElement = new ItemDisplayElement(Items.DIAMOND.getDefaultInstance());
        //displayElement.setTranslation(new Vector3f(-.5f));
        displayElement.setTeleportDuration(2);
        displayElement.setInterpolationDuration(2);
        world.addElement(new DynamicElement(body.getId(), e -> {
            RVec3 pos = new RVec3();
            Quat rot = new Quat();
            bi.getPositionAndRotation(e.physicsBody(), pos, rot);

            displayElement.setOverridePos(new Vec3(pos.x(), pos.y()+0.01, pos.z()));
            displayElement.setLeftRotation(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
            displayElement.startInterpolationIfDirty();
        }, e -> {
            holder.destroy();
            bi.removeBody(e.physicsBody());
        }));

        holder.addElement(displayElement);
        WorldAttachment.of(level, holder, player.position());
    }
}

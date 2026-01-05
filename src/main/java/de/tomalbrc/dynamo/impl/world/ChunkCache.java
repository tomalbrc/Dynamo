package de.tomalbrc.dynamo.impl.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.*;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.mesh.ChunkMeshGenerator;
import de.tomalbrc.dynamo.impl.mesh.MeshPos;
import de.tomalbrc.dynamo.impl.config.ModConfig;
import de.tomalbrc.dynamo.impl.physics.ChunkSectionCollisionShape;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkCache {
    private final ConcurrentHashMap<Long, Integer> terrainObjects = new ConcurrentHashMap<>();
    private final Set<Long> terrainObjectsProcessing = ConcurrentHashMap.newKeySet();
    private final Set<MeshPos> dirty = ConcurrentHashMap.newKeySet();

    public ChunkCache() {

    }

    public void addSectionPhysics(DynamicWorld dynamicWorld, LevelChunk chunk, MeshPos pos) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY())) {
            return;
        }

        final long key = pos.asLong();

        CompletableFuture.runAsync(() -> {
            try {
                BodyInterface bi = dynamicWorld.getPhysicsSystem().getBodyInterface();
                ConstBody result = generateBodyWithMesh(dynamicWorld, pos);


                bi.addBody(result, EActivation.DontActivate);

                terrainObjects.put(key, result.getId());
                Dynamo.LOGGER.info("Collision body stored for {}", pos.toShortString());
            } catch (Throwable t) {
                Dynamo.LOGGER.error("Failed to generate collision for {}: {}", pos.toShortString(), t.getMessage(), t);
            } finally {
                terrainObjectsProcessing.remove(key);
            }
        }, Dynamo.COLLISION_GENERATOR_EXECUTOR);
    }
    private static @NotNull Body generateBodyWithMesh(DynamicWorld dynamicWorld, MeshPos blockPos) {
        final ChunkMeshGenerator.MeshData meshData = ChunkSectionCollisionShape.buildChunkCollisionShape(dynamicWorld.serverLevel, blockPos);

        var empty = meshData == null || meshData.positions == null || meshData.positions.limit() == 0;

        ShapeSettings meshShapeSettings = empty ? new EmptyShapeSettings() : new MeshShapeSettings(meshData.positions);
        BodyCreationSettings bodySettings = new BodyCreationSettings()
                .setMotionType(EMotionType.Static)
                .setObjectLayer(DynamicWorld.objLayerNonMoving)
                .setShape(meshShapeSettings.create().get())
                .setPosition(new RVec3(blockPos.minBlockX(), blockPos.minBlockY(), blockPos.minBlockZ()));

        var bi =  dynamicWorld.getPhysicsSystem().getBodyInterface();

        Body body = bi.createBody(bodySettings);
        bi.addBody(body.getId(), EActivation.DontActivate);

        meshShapeSettings.close();

        return body;
    }

    public void tick(ServerLevel level, DynamicWorld world) {
        Set<BlockPos> interestingPositions = new HashSet<>();

        BodyIdVector idVector = new BodyIdVector();
        world.physicsSystem.getActiveBodies(EBodyType.RigidBody, idVector);

        for (int i = 0; i < idVector.capacity(); i++) {
            var bid = idVector.get(i);
            if (world.physicsSystem.getBodyInterface().getObjectLayer(bid) == DynamicWorld.objLayerMoving) {
                var pos = world.physicsSystem.getBodyInterface().getPosition(bid);

                BlockPos centerBlock = BlockPos.containing(pos.x(), pos.y(), pos.z());

                MeshPos meshCenter = MeshPos.of(centerBlock);
                var meshAround = MeshPos.inSphere(meshCenter, 1);
                meshAround.forEach(m -> {
                    interestingPositions.add(m.center());
                });
            }
        }

        for (BlockPos blockPos : interestingPositions) {
            MeshPos meshPos = MeshPos.of(blockPos);
            long meshKey = meshPos.asLong();

            boolean isPresent = terrainObjects.containsKey(meshKey);
            boolean isProcessing = terrainObjectsProcessing.contains(meshKey);
            boolean isDirty = dirty.contains(meshPos);

            if ((!isPresent && !isProcessing) || isDirty) {
                this.terrainObjectsProcessing.add(meshKey);
                addSectionPhysics(world, level.getChunkAt(blockPos), meshPos);

                if (dirty.remove(meshPos)) {
                    wakeNearbyElements(world, blockPos);
                }
            }
        }
    }

    private void wakeNearbyElements(DynamicWorld world, BlockPos blockPos) {
        var box = new AaBox(new Vec3(0,0,0), 0.5f);
        world.physicsSystem.getBodyInterface().activateBodiesInAaBox(
                box,
                world.physicsSystem.getDefaultBroadPhaseLayerFilter(DynamicWorld.objLayerMoving),
                world.physicsSystem.getDefaultLayerFilter(DynamicWorld.objLayerMoving)
        );
    }

    public void remove(DynamicWorld world, LevelChunk chunk) {
        MeshPos center = MeshPos.of(chunk.getPos().getMiddleBlockPosition(0));
        int r = 16/ModConfig.getInstance().chunkSize;
        MeshPos.inBox(center, r, 128, r).forEach(m -> {
            long key = m.asLong();
            terrainObjectsProcessing.remove(key);
            var removed = terrainObjects.remove(key);
            if (removed != null) {
                world.physicsSystem.getBodyInterface().removeBody(removed);
            }
        });
    }

    public void markDirty(MeshPos meshPos) {
        dirty.add(meshPos);
    }
}
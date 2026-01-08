package de.tomalbrc.dynamo.impl.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.config.ModConfig;
import de.tomalbrc.dynamo.impl.mesh.ChunkMeshGenerator;
import de.tomalbrc.dynamo.impl.mesh.MeshPos;
import de.tomalbrc.dynamo.impl.physics.ChunkSectionCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChunkCache {
    private final Map<Long, Integer> terrainObjects = Collections.synchronizedMap(new HashMap<>());
    private final Map<Long, CompletableFuture<Void>> terrainObjectsProcessing = Collections.synchronizedMap(new HashMap<>());
    private final Set<MeshPos> dirty = ConcurrentHashMap.newKeySet();

    public ChunkCache() {}

    public CompletableFuture<Void> addSectionPhysics(DynamicWorld dynamicWorld, LevelChunk chunk, MeshPos pos, boolean now, Runnable onFinish) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY())) {
            return CompletableFuture.completedFuture(null);
        }

        final long key = pos.asLong();

        if (now) {
            doGen(dynamicWorld, pos, onFinish, key);
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.runAsync(() -> {
                doGen(dynamicWorld, pos, onFinish, key);
            }, Dynamo.COLLISION_GEN);
        }
    }

    private void doGen(DynamicWorld dynamicWorld, MeshPos pos, Runnable onFinish, long key) {
        try {
            BodyInterface bi = dynamicWorld.getPhysicsSystem().getBodyInterface();

            Integer oldId = terrainObjects.remove(key);
            if (oldId != null) {
                bi.removeBody(oldId);
                bi.destroyBody(oldId);
            }

            Body result = generateBodyWithMesh(dynamicWorld, pos);
            bi.addBody(result.getId(), EActivation.DontActivate);

            this.terrainObjects.put(key, result.getId());
            Dynamo.LOGGER.info("Collision body stored for {}", pos.toShortString());

            if (onFinish != null) onFinish.run();
        } catch (Throwable t) {
            Dynamo.LOGGER.error("Failed to generate collision for {}: {}", pos.toShortString(), t.getMessage(), t);
        }
    }

    private static @NotNull Body generateBodyWithMesh(DynamicWorld dynamicWorld, MeshPos blockPos) {
        final ChunkMeshGenerator.MeshData meshData = ChunkSectionCollisionShape.buildChunkCollisionShape(dynamicWorld.serverLevel, blockPos);

        boolean empty = meshData == null || meshData.positions == null || meshData.positions.limit() == 0;

        ShapeSettings meshShapeSettings = empty ? new EmptyShapeSettings() : new MeshShapeSettings(meshData.positions);

        BodyCreationSettings bodySettings = new BodyCreationSettings()
                .setFriction(1f)
                .setRestitution(0f)
                .setMotionType(EMotionType.Static)
                .setObjectLayer(DynamicWorld.objLayerNonMoving)
                .setShapeSettings(meshShapeSettings)
                .setPosition(new RVec3(blockPos.minBlockX(), blockPos.minBlockY(), blockPos.minBlockZ()));

        var bi = dynamicWorld.getPhysicsSystem().getBodyInterface();
        return bi.createBody(bodySettings);
    }

    public void tick(ServerLevel level, DynamicWorld world) {
        Set<MeshPos> interestingPositions = new HashSet<>();
        Set<MeshPos> mainPositions = new HashSet<>();

        BodyIdVector idVector = new BodyIdVector();
        world.physicsSystem.getActiveBodies(EBodyType.RigidBody, idVector);

        for (int i = 0; i < idVector.size(); i++) {
            var bid = idVector.get(i);
            if (world.physicsSystem.getBodyInterface().getObjectLayer(bid) == DynamicWorld.objLayerMoving && world.physicsSystem.getBodyInterface().isActive(bid)) {
                var pos = world.physicsSystem.getBodyInterface().getPosition(bid);

                BlockPos centerBlock = BlockPos.containing(pos.x(), pos.y(), pos.z());
                MeshPos meshCenter = MeshPos.of(centerBlock);

                interestingPositions.addAll(MeshPos.inSphere(meshCenter, 2));
                mainPositions.addAll(MeshPos.inSphere(meshCenter, 1));
            }
        }

        var sss =    interestingPositions.stream().map(x -> x.asLong()).collect(Collectors.toSet());
        if (sss.size() != interestingPositions.size())
            throw new RuntimeException("OOOOOO");

        for (MeshPos meshPos : interestingPositions) {
            long meshKey = meshPos.asLong();

            boolean isPresent = this.terrainObjects.containsKey(meshKey);
            boolean isDirty = this.dirty.contains(meshPos);
            if (isPresent && !isDirty) continue;

            this.terrainObjectsProcessing.computeIfAbsent(meshKey, key -> {
                this.dirty.remove(meshPos);

                return this.addSectionPhysics(
                        world,
                        level.getChunkAt(meshPos.center()),
                        meshPos,
                        mainPositions.contains(meshPos),
                        () -> {
                            if (isDirty) {
                                this.wakeNearbyElements(world, meshPos);
                            }
                        }
                );
            });

            //this.terrainObjectsProcessing.entrySet().removeIf(x -> x.getValue().isDone());

            if (mainPositions.contains(meshPos)) {
                CompletableFuture<Void> currentTask = this.terrainObjectsProcessing.get(meshKey);
                if (currentTask != null && !currentTask.isDone()) {
                    currentTask.join();
                    this.terrainObjectsProcessing.remove(meshKey);
                }
            }
        }
    }

    private void wakeNearbyElements(DynamicWorld world, MeshPos meshPos) {
        float halfSize = (float) ModConfig.getInstance().chunkSize / 2f;
        var box = new AaBox(new Vec3(
                meshPos.minBlockX() + halfSize,
                meshPos.minBlockY() + halfSize,
                meshPos.minBlockZ() + halfSize
        ), halfSize);

        world.physicsSystem.getBodyInterface().activateBodiesInAaBox(
                box,
                world.physicsSystem.getDefaultBroadPhaseLayerFilter(DynamicWorld.objLayerMoving),
                world.physicsSystem.getDefaultLayerFilter(DynamicWorld.objLayerMoving)
        );
    }

    public void remove(DynamicWorld world, LevelChunk chunk) {
        MeshPos center = MeshPos.of(chunk.getPos().getMiddleBlockPosition(0));
        int r = 16 / ModConfig.getInstance().chunkSize - 1;
        MeshPos.inBox(center, r, 128, r).forEach(m -> {
            long key = m.asLong();
            var future = terrainObjectsProcessing.remove(key);
            if (future != null)
                future.cancel(true);

            Integer removedId = terrainObjects.remove(key);
            if (removedId != null) {
                world.physicsSystem.getBodyInterface().removeBody(removedId);
                world.physicsSystem.getBodyInterface().destroyBody(removedId);
            }
        });
    }

    public void markDirty(MeshPos meshPos) {
        dirty.add(meshPos);
    }
}
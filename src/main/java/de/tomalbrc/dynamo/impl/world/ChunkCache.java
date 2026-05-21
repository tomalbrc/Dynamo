package de.tomalbrc.dynamo.impl.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.config.ModConfig;
import de.tomalbrc.dynamo.impl.mesh.ChunkMeshes;
import de.tomalbrc.dynamo.impl.mesh.MeshData;
import de.tomalbrc.dynamo.impl.mesh.MeshPos;
import de.tomalbrc.dynamo.impl.physics.ChunkSectionCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkCache {
    private final Map<Long, Integer> terrainObjects = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Void>> terrainObjectsProcessing = new ConcurrentHashMap<>();
    private final Set<MeshPos> dirty = ConcurrentHashMap.newKeySet();

    private final Map<Long, ChunkMeshes> chunkMeshes = new ConcurrentHashMap<>();

    public ChunkCache() {}

    public CompletableFuture<Void> addSectionPhysics(DynamicWorld dynamicWorld, LevelChunk chunk, MeshPos pos, boolean now, Runnable onFinish) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY())) {
            return CompletableFuture.completedFuture(null);
        }

        long key = pos.asLong();

        if (now) {
            generateAndRegisterBody(dynamicWorld, pos, onFinish, key);
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.runAsync(() -> generateAndRegisterBody(dynamicWorld, pos, onFinish, key), Dynamo.COLLISION_GEN);
        }
    }

    private void generateAndRegisterBody(DynamicWorld dynamicWorld, MeshPos pos, Runnable onFinish, long key) {
        try {
            Body result = generateBodyWithMesh(dynamicWorld, pos);
            Integer oldId = terrainObjects.remove(key);

            BodyInterface bi = dynamicWorld.getPhysicsSystem().getBodyInterface();

            if (oldId != null) {
                bi.removeBody(oldId);
                bi.destroyBody(oldId);
            }

            bi.addBody(result.getId(), EActivation.DontActivate);

            this.terrainObjects.put(key, result.getId());
            Dynamo.LOGGER.debug("Collision body stored for {}", pos.toShortString());

            if (onFinish != null) onFinish.run();
        } catch (Throwable t) {
            Dynamo.LOGGER.error("Failed to generate collision for {}: {}", pos.toShortString(), t.getMessage(), t);
        }
    }

    private @NotNull Body generateBodyWithMesh(DynamicWorld dynamicWorld, MeshPos meshPos) {
        ChunkPos chunkPos = ChunkPos.containing(meshPos.center());
        ChunkMeshes oldMesh = this.chunkMeshes.get(chunkPos.pack());
        MeshData m = oldMesh == null ? null : oldMesh.get(meshPos);
        MeshData meshData = m != null ? m : ChunkSectionCollisionShape.build(dynamicWorld.serverLevel, meshPos);
        boolean empty = meshData == null || meshData.positions == null || meshData.positions.isEmpty();

        if (!empty && (meshData.positions.size() % 3 != 0 || meshData.indices.size() % 3 != 0)) {
            throw new RuntimeException("Mesh data invalid");
        }

        if (m == null && !empty) {
            this.chunkMeshes.computeIfAbsent(chunkPos.pack(), p -> new ChunkMeshes(chunkPos)).put(meshPos, meshData);
        }

        ShapeSettings meshShapeSettings;
        if (empty) {
            meshShapeSettings = new EmptyShapeSettings();
        } else {
            var b = Jolt.newDirectFloatBuffer(meshData.positions.size());
            b.put(meshData.positions.toFloatArray());
            meshShapeSettings = new MeshShapeSettings(b);
        }

        BodyCreationSettings bodySettings = new BodyCreationSettings()
                .setFriction(1f)
                .setRestitution(0f)
                .setMotionType(EMotionType.Static)
                .setObjectLayer(DynamicWorld.objLayerNonMoving)
                .setShapeSettings(meshShapeSettings)
                .setPosition(new RVec3(meshPos.minBlockX(), meshPos.minBlockY(), meshPos.minBlockZ()));

        meshShapeSettings.close();

        BodyInterface bi = dynamicWorld.getPhysicsSystem().getBodyInterface();
        return bi.createBody(bodySettings);
    }

    public void tick(ServerLevel level, DynamicWorld world) {
        Set<MeshPos> interestingPositions = new HashSet<>();
        Set<MeshPos> mainPositions = new HashSet<>();

        BodyIdVector idVector = new BodyIdVector();
        world.physicsSystem.getActiveBodies(EBodyType.RigidBody, idVector);

        BodyInterface bi = world.physicsSystem.getBodyInterface();
        for (int i = 0; i < idVector.size(); i++) {
            int bid = idVector.get(i);
            if (bi.getObjectLayer(bid) == DynamicWorld.objLayerMoving && bi.isActive(bid)) {
                RVec3 pos = bi.getPosition(bid);
                BlockPos centerBlock = BlockPos.containing(pos.x(), pos.y(), pos.z());
                MeshPos meshCenter = MeshPos.of(centerBlock);

                interestingPositions.addAll(MeshPos.inSphere(meshCenter, 2));
                mainPositions.addAll(MeshPos.inSphere(meshCenter, 1));
            }
        }

        this.terrainObjectsProcessing.values().removeIf(CompletableFuture::isDone);

        for (MeshPos meshPos : interestingPositions) {
            long meshKey = meshPos.asLong();

            CompletableFuture<Void> existingTask = this.terrainObjectsProcessing.get(meshKey);
            boolean isHighPriority = mainPositions.contains(meshPos);

            if (existingTask != null) {
                if (isHighPriority && !existingTask.isDone()) {
                    existingTask.join();
                }
                continue;
            }

            boolean isPresent = this.terrainObjects.containsKey(meshKey);
            boolean isDirty = this.dirty.contains(meshPos);

            if (isPresent && !isDirty) continue;

            boolean wasDirty = this.dirty.remove(meshPos);

            CompletableFuture<Void> task = this.addSectionPhysics(
                    world,
                    level.getChunkAt(meshPos.center()),
                    meshPos,
                    isHighPriority,
                    () -> {
                        if (wasDirty) {
                            this.wakeNearbyElements(world, meshPos);
                        }
                    }
            );

            this.terrainObjectsProcessing.put(meshKey, task);
        }
    }

    private void wakeNearbyElements(DynamicWorld world, MeshPos meshPos) {
        float halfSize = (float) ModConfig.getInstance().chunkSize / 2f;
        AaBox box = new AaBox(new Vec3(
                meshPos.minBlockX() + halfSize,
                meshPos.minBlockY() + halfSize,
                meshPos.minBlockZ() + halfSize
        ), halfSize);

        world.physicsSystem.getBodyInterfaceNoLock().activateBodiesInAaBox(
                box,
                world.physicsSystem.getDefaultBroadPhaseLayerFilter(DynamicWorld.objLayerMoving),
                world.physicsSystem.getDefaultLayerFilter(DynamicWorld.objLayerMoving)
        );
    }

    public void remove(DynamicWorld world, LevelChunk chunk) {
        long chunkKey = chunk.getPos().pack();
        CompletableFuture.runAsync(() -> {
            save(chunk);
            this.chunkMeshes.remove(chunkKey);
        }, Util.ioPool());

        MeshPos center = MeshPos.of(chunk.getPos().getMiddleBlockPosition(0));
        int r = 16 / ModConfig.getInstance().chunkSize - 1;

        MeshPos.inBox(center, r, 128, r).forEach(m -> {
            long key = m.asLong();
            CompletableFuture<Void> future = terrainObjectsProcessing.remove(key);
            if (future != null) {
                future.cancel(true);
            }

            Integer removedId = terrainObjects.remove(key);
            if (removedId != null) {
                BodyInterface bodyInterface = world.physicsSystem.getBodyInterface();
                bodyInterface.removeBody(removedId);
                bodyInterface.destroyBody(removedId);
            }
        });
    }

    public void markDirty(MeshPos meshPos) {
        ChunkMeshes m = this.chunkMeshes.get(ChunkPos.containing(meshPos.center()).pack());
        if (m != null) {
            m.remove(meshPos);
        }
        dirty.add(meshPos);
    }

    private Path chunkPath(ChunkPos pos) {
        return ModConfig.CONFIG_DIR.resolve(String.format("cache/chunk-%d-%d.dat", pos.x(), pos.z()));
    }

    public void load(LevelChunk chunk) {
        Path p = chunkPath(chunk.getPos());
        if (Files.exists(p)) {
            ChunkMeshes m = ChunkMeshes.load(p);
            if (m != null) {
                chunkMeshes.put(chunk.getPos().pack(), m);
            }
        }
    }

    public void save(LevelChunk chunk) {
        Path path = chunkPath(chunk.getPos());
        if (!Files.exists(path.getParent())) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                Dynamo.LOGGER.error("Could not create chunk mesh save directory", e);
                return;
            }
        }

        ChunkMeshes m = chunkMeshes.get(chunk.getPos().pack());
        if (m != null) {
            byte[] data = m.save();
            if (data != null && data.length > 0) {
                try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                    fos.write(data);
                } catch (IOException e) {
                    Dynamo.LOGGER.error("Could not save chunk meshes at {}", chunk.getPos());
                }
            }
        }
    }
}
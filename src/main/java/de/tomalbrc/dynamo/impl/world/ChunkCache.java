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
import net.fabricmc.loader.api.FabricLoader;
import net.jpountz.util.ByteBufferUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkCache {
    private final Map<Long, Integer> terrainObjects = Collections.synchronizedMap(new HashMap<>());
    private final Map<Long, CompletableFuture<Void>> terrainObjectsProcessing = Collections.synchronizedMap(new HashMap<>());
    private final Set<MeshPos> dirty = ConcurrentHashMap.newKeySet();

    private final Map<Long, ChunkMeshes> chunkMeshes = Collections.synchronizedMap(new HashMap<>());

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
            Body result = generateBodyWithMesh(dynamicWorld, pos);
            Integer oldId = terrainObjects.remove(key);

            BodyInterface bi = dynamicWorld.getPhysicsSystem().getBodyInterface();

            if (oldId != null) {
                bi.removeBody(oldId);
                bi.destroyBody(oldId);
            }

            bi.addBody(result.getId(), EActivation.DontActivate);

            this.terrainObjects.put(key, result.getId());
            Dynamo.LOGGER.info("Collision body stored for {}", pos.toShortString());

            if (onFinish != null) onFinish.run();
        } catch (Throwable t) {
            Dynamo.LOGGER.error("Failed to generate collision for {}: {}", pos.toShortString(), t.getMessage(), t);
        }
    }

    private @NotNull Body generateBodyWithMesh(DynamicWorld dynamicWorld, MeshPos blockPos) {
        var chunkPos = new ChunkPos(blockPos.center());
        var oldMesh = this.chunkMeshes.get(chunkPos.toLong());
        MeshData m = oldMesh == null ? null : oldMesh.get(blockPos);
        final MeshData meshData = m != null ? m : ChunkSectionCollisionShape.buildChunkCollisionShape(dynamicWorld.serverLevel, blockPos);
        boolean empty = meshData == null || meshData.positions == null || meshData.positions.isEmpty();

        if (!empty && (meshData.positions.size() % 3 != 0 || meshData.indices.size() % 3 != 0 ))
            throw new RuntimeException("oooooo");

        if (m == null && !empty) {
            this.chunkMeshes.computeIfAbsent(chunkPos.toLong(), p -> new ChunkMeshes(chunkPos)).put(blockPos, meshData);
        }

        ShapeSettings meshShapeSettings;
        if (empty) {
            meshShapeSettings = new EmptyShapeSettings();
        }
        else {
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
                .setPosition(new RVec3(blockPos.minBlockX(), blockPos.minBlockY(), blockPos.minBlockZ()));

        meshShapeSettings.close();

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
            var bi = world.physicsSystem.getBodyInterface();
            if (bi.getObjectLayer(bid) == DynamicWorld.objLayerMoving && bi.isActive(bid)) {
                var pos = bi.getPosition(bid);

                BlockPos centerBlock = BlockPos.containing(pos.x(), pos.y(), pos.z());
                MeshPos meshCenter = MeshPos.of(centerBlock);

                interestingPositions.addAll(MeshPos.inSphere(meshCenter, 2));
                mainPositions.addAll(MeshPos.inSphere(meshCenter, 1));
            }
        }

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

            this.terrainObjectsProcessing.entrySet().removeIf(x -> x.getValue().isDone());

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
        long chunkKey = chunk.getPos().toLong();
        CompletableFuture.runAsync(() -> {
            save(chunk);
            this.chunkMeshes.remove(chunkKey);
        }, Util.ioPool());

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
        var m = this.chunkMeshes.get(new ChunkPos(meshPos.center()).toLong());
        if (m != null) {
            m.remove(meshPos);
        }

        dirty.add(meshPos);
    }

    private Path chunkPath(ChunkPos pos) {
        return FabricLoader.getInstance().getGameDir().resolve(String.format("dynamo/chunk-%d-%d.dat", pos.x, pos.z));
    }

    public void load(LevelChunk chunk) {
        var p = chunkPath(chunk.getPos());
        if (Files.exists(p)) {
            var m = ChunkMeshes.load(p);
            if (m != null) {
                chunkMeshes.put(chunk.getPos().toLong(), m);
            }
        }
    }

    public void save(LevelChunk chunk) {
        var path = chunkPath(chunk.getPos());
        if (!Files.exists(path.getParent())) {
            try {
                Files.createDirectories(chunkPath(chunk.getPos()).getParent());
            } catch (IOException e) {
                Dynamo.LOGGER.error("Could not create chunk mesh save directory", e);
                return;
            }
        }

        var p = chunkPath(chunk.getPos());
        var m = chunkMeshes.get(chunk.getPos().toLong());
        if (m != null) {
            var data = m.save();

            if (data != null && data.length > 0) try (FileOutputStream fos = new FileOutputStream(p.toFile())) {
                fos.write(m.save());
            } catch (IOException e) {
                Dynamo.LOGGER.error("Could not save chunk meshes at {}", chunk.getPos());
            }
        }
    }
}
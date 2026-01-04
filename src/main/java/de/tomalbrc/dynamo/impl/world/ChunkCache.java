package de.tomalbrc.dynamo.impl.world;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.EmptyShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.mesh.MeshPos;
import de.tomalbrc.dynamo.impl.config.ModConfig;
import de.tomalbrc.dynamo.impl.physics.ChunkSectionCollisionShape;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.physics.PhysicsThread;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class ChunkCache {
    private final PhysicsThread physicsThread;
    private final Executor collisionExecutor;
    private final ConcurrentHashMap<Long, PhysicsBody> terrainObjects = new ConcurrentHashMap<>();
    private final Set<Long> terrainObjectsProcessing = ConcurrentHashMap.newKeySet();
    private final Set<MeshPos> dirty = ConcurrentHashMap.newKeySet();

    public ChunkCache(PhysicsThread physicsThread) {
        this.physicsThread = Objects.requireNonNull(physicsThread, "physicsThread");
        this.collisionExecutor = Dynamo.COLLISION_GENERATOR_EXECUTOR;
    }

    public void addSectionPhysics(Level level, LevelChunk chunk, MeshPos pos) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY())) {
            return;
        }

        final long key = pos.asLong();

        CompletableFuture.runAsync(() -> {
            try {
                PhysicsRigidBody result = generateBodyWithMesh(level, pos);
                physicsThread.enqueue(space -> space.addCollisionObject(result));
                terrainObjects.put(key, result);
                Dynamo.LOGGER.info("Collision body stored for {}", pos.toShortString());
            } catch (Throwable t) {
                Dynamo.LOGGER.error("Failed to generate collision for {}: {}", pos.toShortString(), t.getMessage(), t);
            } finally {
                terrainObjectsProcessing.remove(key);
            }
        }, collisionExecutor);
    }

    private static @NotNull PhysicsRigidBody generateBodyWithMesh(Level level, MeshPos blockPos) {
        final ChunkSectionCollisionShape sectionCollisionShape = new ChunkSectionCollisionShape(level, blockPos);
        final int childCount = sectionCollisionShape.countChildren();
        final CollisionShape shape = (childCount == 0) ? new EmptyShape(true) : sectionCollisionShape;

        Dynamo.LOGGER.info("Adding chunk section, tri-count: {} {}", childCount, blockPos.toShortString());

        PhysicsRigidBody body = new PhysicsRigidBody(shape, 0);
        body.setKinematic(true);
        body.setFriction(1.0f);
        body.setRestitution(0f);

        return body;
    }

    public void tick(ServerLevel level, DynamicWorld world) {
        Set<BlockPos> interestingPositions = new HashSet<>();
        Set<MeshPos> keepMeshPositions = new HashSet<>();

        world.physicsSpace.getRigidBodyList().forEach(x -> {
            if (x.isStatic())
                return;

            var transform = x.getTransform(null);
            var pos = transform.getTranslation();
            BlockPos centerBlock = BlockPos.containing(pos.x, pos.y, pos.z);

            MeshPos meshCenter = MeshPos.of(centerBlock);
            var meshAround = MeshPos.inSphere(meshCenter, 2);
            meshAround.forEach(m -> {
                interestingPositions.add(m.center());
            });
        });

        for (BlockPos blockPos : interestingPositions) {
            MeshPos meshPos = MeshPos.of(blockPos);
            long meshKey = meshPos.asLong();

            boolean isPresent = terrainObjects.containsKey(meshKey);
            boolean isProcessing = terrainObjectsProcessing.contains(meshKey);
            boolean isDirty = dirty.contains(meshPos);

            if ((!isPresent && !isProcessing) || isDirty) {
                PhysicsBody oldBody = terrainObjects.get(meshKey);

                terrainObjectsProcessing.add(meshKey);
                addSectionPhysics(level, level.getChunkAt(blockPos), meshPos);

                if (dirty.remove(meshPos)) {
                    wakeNearbyElements(world, blockPos);
                }

                if (oldBody != null) {
                    physicsThread.enqueue(space -> space.removeCollisionObject(oldBody));
                    terrainObjects.remove(meshKey, oldBody);
                }
            }
        }
    }

    private void wakeNearbyElements(DynamicWorld world, BlockPos blockPos) {
        for (DynamicElement element : world.getElements()) {
            var tr = element.physicsBody().getTransform(null).getTranslation();
            if (blockPos.distToCenterSqr(tr.x, tr.y, tr.z) < 3f) {
                element.physicsBody().activate(false);
            }
        }
    }

    public void remove(LevelChunk chunk) {
        MeshPos center = MeshPos.of(chunk.getPos().getMiddleBlockPosition(0));
        int r = 16/ModConfig.getInstance().chunkSize;
        MeshPos.inBox(center, r, 128, r).forEach(m -> {
            long key = m.asLong();
            terrainObjectsProcessing.remove(key);
            PhysicsBody removed = terrainObjects.remove(key);
            if (removed != null) {
                physicsThread.enqueue(space -> space.removeCollisionObject(removed));
            }
        });
    }

    public void markDirty(MeshPos meshPos) {
        dirty.add(meshPos);
    }
}
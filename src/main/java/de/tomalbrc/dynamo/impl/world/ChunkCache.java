package de.tomalbrc.dynamo.impl.world;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.EmptyShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.MeshPos;
import de.tomalbrc.dynamo.impl.config.ModConfig;
import de.tomalbrc.dynamo.impl.physics.ChunkSectionCollisionShape;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.physics.PhysicsThread;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChunkCache {
    private final PhysicsThread physicsThread;
    private final Map<Long, PhysicsBody> terrainObjects;
    private final Set<Long> terrainObjectsProcessing;

    private final Set<MeshPos> dirty = new HashSet<>();

    public ChunkCache(PhysicsThread physicsThread) {
        this.terrainObjects = Collections.synchronizedMap(new HashMap<>());
        this.terrainObjectsProcessing = Collections.synchronizedSet(new LongOpenHashSet());
        this.physicsThread = physicsThread;
    }

    public void addSectionPhysics(Level level, LevelChunk chunk, MeshPos pos) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY()))
            return;

        CompletableFuture.runAsync(() -> {
            Result result = generateBodyWithMesh(level, pos);

            this.physicsThread.enqueue(space -> space.addCollisionObject(result.body));

            this.terrainObjects.put(pos.asLong(), result.body());
            this.terrainObjectsProcessing.remove(pos.asLong());

        }, Dynamo.COLLISION_GENERATOR_EXECUTOR);
    }

    private static @NotNull Result generateBodyWithMesh(Level level, MeshPos blockPos) {
        CollisionShape shapeF;
        ChunkSectionCollisionShape sectionCollisionShape = new ChunkSectionCollisionShape(level, blockPos);
        if (sectionCollisionShape.countChildren() == 0)
            shapeF = new EmptyShape(true);
        else shapeF = sectionCollisionShape;

        Dynamo.LOGGER.info("Adding chunk section, tri-count: {} {}", sectionCollisionShape.countChildren(), blockPos.toShortString());

        var body = new PhysicsRigidBody(shapeF, 0);
        body.setKinematic(true);
        body.setFriction(1.f);
        body.setRestitution(0f);

        return new Result(sectionCollisionShape, body);
    }

    private record Result(ChunkSectionCollisionShape sectionCollisionShape, PhysicsRigidBody body) {
    }

    public void tick(ServerLevel level, DynamicWorld world) {
        Set<BlockPos> positions = new ObjectArraySet<>();
        Set<MeshPos> keep = new ObjectArraySet<>();

        for (var e : world.getElements()) {
            var physicsBody = e.physicsBody();

            var transform = physicsBody.getTransform(null);
            var pos = transform.getTranslation();
            var blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
            positions.add(blockPos);

            for (Direction value : Direction.values()) {
                positions.add(blockPos.relative(value, ModConfig.getInstance().chunkSize));
            }

            MeshPos meshPos = MeshPos.of(blockPos);
            var rad = 1;
            var p = MeshPos.around(meshPos, rad, rad, rad);
            p.forEach(x -> positions.add(x.center()));
            //keep.addAll(p.toList());
        }

//        this.terrainObjects.entrySet().removeIf(x -> {
//            var remove = !keep.contains(x.getKey());
//            if (remove) {
//                var physicsBody = x.getValue();
//                if (physicsBody != null) {
//                    this.physicsThread.enqueue(space -> space.removeCollisionObject(physicsBody));
//                }
//            }
//
//            return remove;
//        });

        for (BlockPos blockPos : positions) {
            MeshPos meshPos = MeshPos.of(blockPos);

            if ((!this.terrainObjects.containsKey(meshPos.asLong()) && !this.terrainObjectsProcessing.contains(meshPos.asLong())) || this.dirty.contains(meshPos)) {
                var oldPhysicsBody = this.terrainObjects.get(MeshPos.of(blockPos).asLong());

                this.terrainObjectsProcessing.add(meshPos.asLong());
                this.addSectionPhysics(level, level.getChunkAt(blockPos), meshPos);
                boolean didRemove = this.dirty.remove(meshPos);

                if (oldPhysicsBody != null)
                    this.physicsThread.enqueue(space -> space.remove(oldPhysicsBody));
                if (didRemove) {
                    for (DynamicElement element : world.getElements()) {
                        var tr = element.physicsBody().getTransform(null).getTranslation();
                        var x = blockPos.distToCenterSqr(tr.x, tr.y, tr.z);
                        if (x < 3f) {
                            element.physicsBody().activate(false);
                        }
                    }
                }
            }
        }
    }

    public void remove(LevelChunk chunk) {
        // TODO: keep cached longer?
        var p = MeshPos.around(MeshPos.of(chunk.getPos().getMiddleBlockPosition(0)), 0, 64, 64);
        p.forEach(x -> {
            var physicsBody = this.terrainObjects.remove(x.asLong());
            if (physicsBody != null) {
                this.physicsThread.enqueue(space -> space.removeCollisionObject(physicsBody));
            }
        });
    }

    public void markDirty(MeshPos of) {
        dirty.add(of);
    }
}

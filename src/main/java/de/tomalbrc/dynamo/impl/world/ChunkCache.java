package de.tomalbrc.dynamo.impl.world;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.EmptyShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.physics.PhysicsThread;
import de.tomalbrc.dynamo.impl.physics.ChunkSectionCollisionShape;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkCache {
    private final PhysicsThread physicsThread;
    private final Map<SectionPos, CompletableFuture<PhysicsBody>> bodyMap;
    private final Set<SectionPos> dirty = new HashSet<>();

    private static final Map<BlockState, VoxelShape> SHAPE_CACHE = new Reference2ReferenceOpenHashMap<>();

    public ChunkCache(PhysicsThread physicsThread) {
        this.bodyMap = new ConcurrentHashMap<>();
        this.physicsThread = physicsThread;
    }

    public @Nullable PhysicsBody getPhysicsBody(SectionPos pos) {
        return bodyMap.get(pos).getNow(null);
    }

    public CompletableFuture<PhysicsBody> addSectionPhysics(LevelChunk chunk, SectionPos pos) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY()))
            return null;

        CompletableFuture<PhysicsBody> future = CompletableFuture.supplyAsync(() -> {
            CollisionShape shapeF;
            ChunkSectionCollisionShape shape = new ChunkSectionCollisionShape(chunk, pos);
            if (shape.countChildren() == 0)
                shapeF = new EmptyShape(true);
            else shapeF = shape;

            Dynamo.LOGGER.info("Adding chunk section, tri-count: {} {}", shape.countChildren(), pos.toShortString());

            var body = new PhysicsRigidBody(shapeF, 0);
            body.setKinematic(true);
            body.setFriction(1.f);
            body.setRestitution(0f);

            this.physicsThread.enqueue(space -> space.addCollisionObject(body));

            return body;
        }, Dynamo.COLLISION_GENERATOR_EXECUTOR);

        this.bodyMap.put(pos, future);

        return future;
    }

    public void tick(ServerLevel level, DynamicWorld world) {
        Set<BlockPos> positions = new ObjectArraySet<>();
        Set<SectionPos> keep = new ObjectArraySet<>();

        for (var e : world.getElements()) {
            var physicsBody = e.physicsBody();

            var transform = physicsBody.getTransform(null);
            var pos = transform.getTranslation();
            var blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
            positions.add(blockPos);

            for (Direction value : Direction.values()) {
                positions.add(blockPos.relative(value, 16));
            }

            SectionPos sectionPos = SectionPos.of(blockPos);
            var p = SectionPos.aroundChunk(sectionPos.chunk(), 1, sectionPos.y()-1, sectionPos.y()+1);
            keep.addAll(p.toList());
        }

        this.bodyMap.entrySet().removeIf(x -> {
            var remove = !keep.contains(x.getKey());
            if (remove && !x.getValue().isDone())
                x.getValue().cancel(true);
            else if (remove) {
                var physicsBody = x.getValue().getNow(null);
                if (physicsBody != null) {
                    this.physicsThread.enqueue(space -> space.removeCollisionObject(physicsBody));
                }
            }

            return remove;
        });

        for (BlockPos blockPos : positions) {
            SectionPos sectionPos = SectionPos.of(blockPos);

            if (!this.bodyMap.containsKey(sectionPos) || this.dirty.contains(sectionPos)) {
                var oldFuture = this.bodyMap.get(SectionPos.of(blockPos));

                var future = this.addSectionPhysics(level.getChunkAt(blockPos), sectionPos);
                boolean didRemove = this.dirty.remove(sectionPos);

                var oldPhysicsBody = oldFuture == null ? null : oldFuture.getNow(null);
                if (oldFuture != null)
                    oldFuture.cancel(true);

                if (future != null) future.thenAcceptAsync((newBody) -> {
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
                }, Dynamo.SERVER);
            }
        }
    }

    public void remove(LevelChunk chunk) {
        // TODO: keep cached longer?
        var p = SectionPos.aroundChunk(chunk.getPos(), 0, 0, 32);
        p.forEach(x -> {
            var fut = this.bodyMap.remove(x);
            if (fut != null) {
                var removed = fut.getNow(null);
                fut.cancel(true);

                if (removed != null)
                    this.physicsThread.enqueue(space -> space.removeCollisionObject(removed));
            }
        });
    }

    public void markDirty(SectionPos of) {
        dirty.add(of);
    }
}

package de.tomalbrc.dynamo.impl.world;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.DynamicElement;
import de.tomalbrc.dynamo.impl.geo.ChunkSectionCollisionShape;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChunkCache {
    private final PhysicsSpace physicsSpace;
    private final Map<SectionPos, CompletableFuture<PhysicsBody>> bodyMap;
    private final Set<SectionPos> dirty = new HashSet<>();

    public ChunkCache(PhysicsSpace physicsSpace) {
        this.bodyMap = new HashMap<>();
        this.physicsSpace = physicsSpace;
    }

    public @Nullable PhysicsBody getPhysicsBody(SectionPos pos) {
        return bodyMap.get(pos).getNow(null);
    }

    public CompletableFuture<PhysicsBody> add(LevelChunk chunk, SectionPos pos) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY()))
            return null;

        CompletableFuture<PhysicsBody> future = CompletableFuture.supplyAsync(() -> {
            var shape = new ChunkSectionCollisionShape(chunk, pos);
            Dynamo.LOGGER.info("Adding chunk section, count: {} {}", shape.countChildren(), pos.toShortString());

            var body = new PhysicsRigidBody(shape, 0);
            body.setFriction(1.f);
            body.setRestitution(0f);

            Dynamo.SERVER.execute(() -> {
                this.physicsSpace.addCollisionObject(body);
            });

            return body;
        }, Dynamo.EXECUTOR);

        this.bodyMap.put(pos, future);

        return future;
    }

    public void tick(ServerLevel level, Collection<DynamicElement> elements) {
        Set<BlockPos> positions = new ObjectArraySet<>();
        Set<SectionPos> keep = new ObjectArraySet<>();

        for (var e : elements) {
            var physicsBody = e.physicsBody();

            var transform = physicsBody.getTransform(null);
            var pos = transform.getTranslation();
            var blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
            positions.add(blockPos);

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
                if (physicsBody != null)
                    this.physicsSpace.removeCollisionObject(physicsBody);
            }

            return remove;
        });

        for (BlockPos blockPos : positions) {
            SectionPos sectionPos = SectionPos.of(blockPos);

            if (!this.bodyMap.containsKey(sectionPos) || this.dirty.contains(sectionPos)) {
                var oldFuture = this.bodyMap.get(SectionPos.of(blockPos));

                var future = this.add(level.getChunkAt(blockPos), sectionPos);
                boolean didRemove = this.dirty.remove(sectionPos);

                var oldPhysicsBody = oldFuture == null ? null : oldFuture.getNow(null);
                if (oldFuture != null)
                    oldFuture.cancel(true);

                future.thenAcceptAsync((newBody) -> {
                    if (oldPhysicsBody != null)
                        this.physicsSpace.remove(oldPhysicsBody);
                    if (didRemove) {
                        for (DynamicElement element : elements) {
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
                    this.physicsSpace.removeCollisionObject(removed);
            }
        });
    }

    public void remove(PhysicsBody body) {
        this.physicsSpace.removeCollisionObject(body);
    }

    public void markDirty(SectionPos of) {
        dirty.add(of);
    }
}

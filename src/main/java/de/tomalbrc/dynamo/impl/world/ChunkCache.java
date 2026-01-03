package de.tomalbrc.dynamo.impl.world;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.EmptyShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.physics.ChunkSectionCollisionShape;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.physics.PhysicsThread;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChunkCache {
    private final PhysicsThread physicsThread;
    private final Map<Long, PhysicsBody> terrainObjects;
    private final Set<Long> terrainObjectsProcessing;

    private final Set<SectionPos> dirty = new HashSet<>();

    private static final Map<BlockState, VoxelShape> SHAPE_CACHE = new Reference2ReferenceOpenHashMap<>();

    public ChunkCache(PhysicsThread physicsThread) {
        this.terrainObjects = Collections.synchronizedMap(new HashMap<>());
        this.terrainObjectsProcessing = Collections.synchronizedSet(new LongOpenHashSet());
        this.physicsThread = physicsThread;
    }

    public @Nullable PhysicsBody getPhysicsBody(SectionPos pos) {
        return terrainObjects.get(pos);
    }

    public void addSectionPhysics(Level level, LevelChunk chunk, SectionPos pos) {
        if (!chunk.isInsideBuildHeight(pos.maxBlockY()) || !chunk.isInsideBuildHeight(pos.minBlockY()))
            return;

        this.physicsThread.enqueue(space -> {
            CollisionShape shapeF;
            ChunkSectionCollisionShape sectionCollisionShape = new ChunkSectionCollisionShape(level, pos, false);
            if (sectionCollisionShape.countChildren() == 0)
                shapeF = new EmptyShape(true);
            else shapeF = sectionCollisionShape;

            Dynamo.LOGGER.info("Adding chunk section, tri-count: {} {}", sectionCollisionShape.countChildren(), pos.toShortString());

            var body = new PhysicsRigidBody(shapeF, 0);
            body.setKinematic(true);
            body.setFriction(1.f);
            body.setRestitution(0f);

            space.addCollisionObject(body);

            this.terrainObjects.put(pos.asLong(), body);
            this.terrainObjectsProcessing.remove(pos.asLong());

            if (sectionCollisionShape.countChildren() > 0) {
                sectionCollisionShape.smoothFuture().thenAcceptAsync(x -> {
                    sectionCollisionShape.removeChildShape(sectionCollisionShape.simpleShape);
                    sectionCollisionShape.addChildShape(x);
                    this.physicsThread.enqueue(s -> body.rebuildRigidBody());
                });
            }

        });
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
            var rad = 1;
            var p = SectionPos.aroundChunk(sectionPos.chunk(), rad, sectionPos.y()-rad, sectionPos.y()+rad);
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
            SectionPos sectionPos = SectionPos.of(blockPos);

            if ((!this.terrainObjects.containsKey(sectionPos.asLong()) && !this.terrainObjectsProcessing.contains(sectionPos.asLong())) || this.dirty.contains(sectionPos)) {
                var oldPhysicsBody = this.terrainObjects.get(SectionPos.of(blockPos).asLong());

                this.terrainObjectsProcessing.add(sectionPos.asLong());
                this.addSectionPhysics(level, level.getChunkAt(blockPos), sectionPos);
                boolean didRemove = this.dirty.remove(sectionPos);

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
        var p = SectionPos.aroundChunk(chunk.getPos(), 0, 0, 32);
        p.forEach(x -> {
            var physicsBody = this.terrainObjects.remove(x.asLong());
            if (physicsBody != null) {
                this.physicsThread.enqueue(space -> space.removeCollisionObject(physicsBody));
            }
        });
    }

    public void markDirty(SectionPos of) {
        dirty.add(of);
    }
}

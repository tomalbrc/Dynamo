package de.tomalbrc.dynamo.impl.world;

import com.jme3.bullet.PhysicsSpace;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.physics.PhysicsThread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

public class DynamicWorld {
    protected final ChunkCache chunkCache;
    protected final HashSet<DynamicElement> elements = new HashSet<>();

    protected final PhysicsThread physicsThread;
    protected final PhysicsSpace physicsSpace;

    public DynamicWorld() {
        this.physicsThread = new PhysicsThread();
        this.physicsSpace = this.getPhysicsThread().getPhysicsSpace();
        this.physicsSpace.setMaxSubSteps(4);
        this.physicsSpace.setAccuracy(1f/60f);

        this.chunkCache = new ChunkCache(this.physicsThread);
    }

    public PhysicsSpace getPhysicsSpace() {
        return this.physicsSpace;
    }

    public void close() {
        this.physicsThread.close();
        this.physicsSpace.destroy();
    }

    public void addElement(DynamicElement element) {
        this.elements.add(element);
    }

    public Set<DynamicElement> getElements() {
        return this.elements;
    }

    public void updateBlock(Level level, BlockState blockState, BlockPos blockPos) {
        this.chunkCache.markDirty(SectionPos.of(blockPos));
    }

    public void unloadChunk(ServerLevel level, LevelChunk chunk) {
        this.chunkCache.remove(chunk);
    }

    public void tick(ServerLevel serverLevel) {
        boolean skip = serverLevel.getGameTime() % 2 != 0;
        if (!skip) {
            this.getPhysicsThread().enqueue(space -> this.chunkCache.tick(serverLevel, this));
            this.elements.forEach(DynamicElement::update);
        }
    }

    public PhysicsThread getPhysicsThread() {
        return physicsThread;
    }
}

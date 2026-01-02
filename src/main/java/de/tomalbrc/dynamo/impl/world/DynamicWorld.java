package de.tomalbrc.dynamo.impl.world;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RayTestFlag;
import de.tomalbrc.dynamo.DynamicElement;
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
    protected final PhysicsSpace physicsSpace;

    public DynamicWorld() {
        this.physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        this.physicsSpace.setMaxSubSteps(6);
        this.physicsSpace.setAccuracy(1f/60f);

        this.chunkCache = new ChunkCache(physicsSpace);
    }

    public PhysicsSpace getPhysicsSpace() {
        return this.physicsSpace;
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

        this.physicsSpace.update(serverLevel.tickRateManager().millisecondsPerTick()/1000f);

        boolean skip = serverLevel.getGameTime() % 2 != 0;
        if (!skip) {
            this.chunkCache.tick(serverLevel, this.elements);
            this.elements.forEach(DynamicElement::update);
        }
    }
}

package de.tomalbrc.dynamo.impl.world;

import com.github.stephengold.joltjni.*;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.mesh.MeshPos;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

public class DynamicWorld {
    final public static int numObjLayers = 2;
    /**
     * object layer for moving objects
     */
    final public static int objLayerMoving = 0;
    /**
     * object layer for non-moving objects
     */
    final public static int objLayerNonMoving = 1;

    protected final ChunkCache chunkCache;
    protected final HashSet<DynamicElement> elements = new HashSet<>();

    protected final int numWorkerThreads;

    protected final JobSystem jobSystem;

    protected final TempAllocator tempAllocator = new TempAllocatorMalloc();
    protected final PhysicsSystem physicsSystem;

    protected final ServerLevel serverLevel;

    public DynamicWorld(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;

        this.numWorkerThreads = Runtime.getRuntime().availableProcessors();
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numWorkerThreads);

        this.physicsSystem = createSystem();

        this.chunkCache = new ChunkCache();
    }

    public PhysicsSystem getPhysicsSystem() {
        return this.physicsSystem;
    }

    public void close() {
        this.physicsSystem.close();
    }

    public void addElement(DynamicElement element) {
        this.elements.add(element);
    }

    public Set<DynamicElement> getElements() {
        return this.elements;
    }

    public void updateBlock(Level level, BlockState blockState, BlockPos blockPos) {
        this.chunkCache.markDirty(MeshPos.of(blockPos));
    }

    public void unloadChunk(ServerLevel level, LevelChunk chunk) {
        this.chunkCache.remove(this, chunk);
    }

    public void tick(ServerLevel serverLevel) {
        boolean skip = serverLevel.getGameTime() % 2 != 0;
        if (true || !skip) {

            Dynamo.PHYSICS.execute(() -> {
                this.chunkCache.tick(serverLevel, this);

                float timePerStep = 0.1f; // in seconds
                int numCollisionSteps = 5;
                this.physicsSystem.update(timePerStep, numCollisionSteps, this.tempAllocator, jobSystem);
            });
        }

        this.elements.forEach(DynamicElement::update);
    }

    public JobSystem getJobSystem() {
        return this.jobSystem;
    }

    private static PhysicsSystem createSystem() {
        int numBpLayers = 1;

        ObjectLayerPairFilterTable ovoFilter = new ObjectLayerPairFilterTable(numObjLayers);
        ovoFilter.enableCollision(objLayerMoving, objLayerMoving);
        ovoFilter.enableCollision(objLayerMoving, objLayerNonMoving);
        ovoFilter.disableCollision(objLayerNonMoving, objLayerNonMoving);

        BroadPhaseLayerInterfaceTable layerMap = new BroadPhaseLayerInterfaceTable(numObjLayers, numBpLayers);
        layerMap.mapObjectToBroadPhaseLayer(objLayerMoving, 0);
        layerMap.mapObjectToBroadPhaseLayer(objLayerNonMoving, 0);

        ObjectVsBroadPhaseLayerFilter ovbFilter = new ObjectVsBroadPhaseLayerFilterTable(layerMap, numBpLayers, ovoFilter, numObjLayers);

        PhysicsSystem system = new PhysicsSystem();

        int maxBodies = 8_000;
        int numBodyMutexes = 0; // 0 means "use the default number"
        int maxBodyPairs = 65_536;
        int maxContacts = 20_480;
        system.init(maxBodies, numBodyMutexes, maxBodyPairs, maxContacts, layerMap, ovbFilter, ovoFilter);

        return system;
    }
}

package de.tomalbrc.dynamo.impl.command;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChunkSectionCollisionShape:
 * - Greedy merged boxes for full blocks (fast).
 * - Cached per-BlockState AABB decomposition for non-full blocks (accurate but controlled).
 */
public class ChunkSectionCollisionShapeX extends CompoundCollisionShape {
    private final SectionPos pos;

    static final int CHUNK_SIZE_X = 16;
    static final int CHUNK_SIZE_Z = 16;

    // Cache: BlockState -> list of block-local AABBs (minX,minY,minZ,maxX,maxY,maxZ) in block coords [0..1]
    private static final Map<BlockState, List<BoxSpec>> STATE_AABBS_CACHE = new ConcurrentHashMap<>();

    // Cache: half-extents key -> BoxCollisionShape instance (to avoid creating many identical shapes)
    private static final Map<BoxKey, BoxCollisionShape> BOX_SHAPE_CACHE = new ConcurrentHashMap<>();

    // small epsilon for floating comparisons
    private static final double EPS = 1e-6;

    public ChunkSectionCollisionShapeX(LevelChunk chunk, SectionPos sectionPos) {
        this.pos = sectionPos;
        buildChunkCollisionShape(chunk);
    }

    public void buildChunkCollisionShape(LevelChunk chunk) {
        int baseX = pos.minBlockX();
        int baseZ = pos.minBlockZ();
        int minY = pos.minBlockY();
        int maxY = pos.maxBlockY() + 1;
        int height = maxY - minY;
        if (height <= 0) return;

        // 3D grid marking "full-block solids" for greedy merging
        boolean[][][] fullBlockSolid = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];
        // We'll still keep track of non-empty blocks so we can add their cached AABBs later
        boolean[][][] nonEmpty = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];

        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

        // First pass: classify each block as empty / full-block-solid / non-full (but non-empty)
        for (int x = 0; x < CHUNK_SIZE_X; x++) {
            for (int yi = 0; yi < height; yi++) {
                for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                    tmp.set(baseX + x, minY + yi, baseZ + z);
                    BlockState st = chunk.getBlockState(tmp);
                    VoxelShape shape = st.getCollisionShape(chunk, tmp);

                    if (shape.isEmpty()) {
                        nonEmpty[x][yi][z] = false;
                        fullBlockSolid[x][yi][z] = false;
                        continue;
                    }

                    nonEmpty[x][yi][z] = true;

                    // decide if the shape is a full 1x1x1 block by examining the shape's boxes
                    if (isFullBlockShape(shape)) {
                        fullBlockSolid[x][yi][z] = true;
                    } else {
                        fullBlockSolid[x][yi][z] = false;
                    }

                    // Pre-cache the decomposition for non-full shapes (cache per BlockState)
                    if (!fullBlockSolid[x][yi][z]) {
                        // compute & store per-block AABBs (in block-local coords)
                        STATE_AABBS_CACHE.computeIfAbsent(st, bs -> decomposeShapeToBoxes(shape));
                    }
                }
            }
        }

        // Greedy merge only the fullBlockSolid grid (this is your original algorithm)
        boolean[][][] used = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];
        boolean[][] mask = new boolean[CHUNK_SIZE_Z][CHUNK_SIZE_X];

        for (int yi = 0; yi < height; yi++) {
            // build mask for this layer
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CHUNK_SIZE_X; x++) {
                    mask[z][x] = fullBlockSolid[x][yi][z] && !used[x][yi][z];
                }
            }

            // greedy rect packing on mask
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CHUNK_SIZE_X; x++) {
                    if (!mask[z][x]) continue;

                    int w = 1;
                    while (x + w < CHUNK_SIZE_X && mask[z][x + w]) w++;

                    int h = 1;
                    outer:
                    while (z + h < CHUNK_SIZE_Z) {
                        for (int k = 0; k < w; k++) {
                            if (!mask[z + h][x + k]) break outer;
                        }
                        h++;
                    }

                    for (int dz = 0; dz < h; dz++) {
                        for (int dx = 0; dx < w; dx++) {
                            mask[z + dz][x + dx] = false;
                        }
                    }

                    int rectX0 = x;
                    int rectX1 = x + w; // exclusive
                    int rectZ0 = z;
                    int rectZ1 = z + h; // exclusive

                    // grow vertically
                    int boxHeight = 1;
                    vertical:
                    while (yi + boxHeight < height) {
                        for (int zz = rectZ0; zz < rectZ1; zz++) {
                            for (int xx = rectX0; xx < rectX1; xx++) {
                                if (!fullBlockSolid[xx][yi + boxHeight][zz] || used[xx][yi + boxHeight][zz]) {
                                    break vertical;
                                }
                            }
                        }
                        boxHeight++;
                    }

                    // mark used
                    for (int by = 0; by < boxHeight; by++) {
                        for (int zz = rectZ0; zz < rectZ1; zz++) {
                            for (int xx = rectX0; xx < rectX1; xx++) {
                                used[xx][yi + by][zz] = true;
                            }
                        }
                    }

                    // create one BoxCollisionShape for that box
                    float sizeX = rectX1 - rectX0;
                    float sizeY = boxHeight;
                    float sizeZ = rectZ1 - rectZ0;

                    float hx = sizeX * 0.5f;
                    float hy = sizeY * 0.5f;
                    float hz = sizeZ * 0.5f;

                    float cx = baseX + rectX0 + hx;
                    float cy = minY + yi + hy;
                    float cz = baseZ + rectZ0 + hz;

                    BoxCollisionShape box = cachedBoxCollisionShape(hx, hy, hz);
                    this.addChildShape(box, new Vector3f(cx, cy, cz));
                }
            }
        }

        // After merging full blocks, add per-block AABBs for every non-full block (cached decomposition).
        // We do this in a second pass to avoid mixing with the merged grid.
        for (int x = 0; x < CHUNK_SIZE_X; x++) {
            for (int yi = 0; yi < height; yi++) {
                for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                    if (!nonEmpty[x][yi][z]) continue; // empty
                    if (fullBlockSolid[x][yi][z]) continue; // already handled by merged boxes

                    tmp.set(baseX + x, minY + yi, baseZ + z);
                    BlockState st = chunk.getBlockState(tmp);

                    List<BoxSpec> boxes = STATE_AABBS_CACHE.get(st);
                    if (boxes == null || boxes.isEmpty()) continue;

                    // add each box from the block-local decomposition
                    final float blockOriginX = baseX + x;
                    final float blockOriginY = minY + yi;
                    final float blockOriginZ = baseZ + z;

                    for (BoxSpec bs : boxes) {
                        // convert block-local center to world coordinates
                        float cx = blockOriginX + bs.centerX;
                        float cy = blockOriginY + bs.centerY;
                        float cz = blockOriginZ + bs.centerZ;

                        BoxCollisionShape boxShape = cachedBoxCollisionShape(bs.hx, bs.hy, bs.hz);
                        this.addChildShape(boxShape, new Vector3f(cx, cy, cz));
                    }
                }
            }
        }
    }

    // Helper: check whether a VoxelShape represents a single full block AABB [0..1]^3
    private static boolean isFullBlockShape(VoxelShape shape) {
        return shape.equals(Shapes.block());
    }

    // Decompose a non-full VoxelShape into a list of block-local BoxSpec's
    private static List<BoxSpec> decomposeShapeToBoxes(VoxelShape shape) {
        List<BoxSpec> list = new ArrayList<>(4);
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            // convert to center + half-extents in block-local coords
            float hx = (float) ((maxX - minX) * 0.5);
            float hy = (float) ((maxY - minY) * 0.5);
            float hz = (float) ((maxZ - minZ) * 0.5);
            float centerX = (float) (minX + hx);
            float centerY = (float) (minY + hy);
            float centerZ = (float) (minZ + hz);

            // guard against degenerate boxes
            if (hx <= 1e-6f || hy <= 1e-6f || hz <= 1e-6f) return;

            list.add(new BoxSpec(hx, hy, hz, centerX, centerY, centerZ));
        });
        return list;
    }

    // Get or create a BoxCollisionShape for half-extents (hx,hy,hz). Reuse shape instances.
    private static BoxCollisionShape cachedBoxCollisionShape(float hx, float hy, float hz) {
        BoxKey key = new BoxKey(hx, hy, hz);
        return BOX_SHAPE_CACHE.computeIfAbsent(key, k -> new BoxCollisionShape(new Vector3f(k.hx, k.hy, k.hz)));
    }

    // Simple immutable "spec" describing a box in block-local coordinates:
    private static final class BoxSpec {
        final float hx, hy, hz;         // half-extents (in blocks)
        final float centerX, centerY, centerZ; // center (0..1) relative to block origin

        BoxSpec(float hx, float hy, float hz, float centerX, float centerY, float centerZ) {
            this.hx = hx;
            this.hy = hy;
            this.hz = hz;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
        }
    }

    // Key for caching BoxCollisionShape instances
    private static final class BoxKey {
        final float hx, hy, hz;

        BoxKey(float hx, float hy, float hz) {
            // Normalize small float differences to avoid many near-duplicate keys:
            this.hx = normalizeKey(hx);
            this.hy = normalizeKey(hy);
            this.hz = normalizeKey(hz);
        }

        private static float normalizeKey(float v) {
            // round to small precision to avoid floating point jitter causing too many keys
            final float PREC = 1e-4f;
            return Math.round(v / PREC) * PREC;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BoxKey)) return false;
            BoxKey k = (BoxKey) o;
            return Float.floatToIntBits(hx) == Float.floatToIntBits(k.hx)
                    && Float.floatToIntBits(hy) == Float.floatToIntBits(k.hy)
                    && Float.floatToIntBits(hz) == Float.floatToIntBits(k.hz);
        }

        @Override
        public int hashCode() {
            int h = Float.hashCode(hx);
            h = 31 * h + Float.hashCode(hy);
            h = 31 * h + Float.hashCode(hz);
            return h;
        }
    }
}

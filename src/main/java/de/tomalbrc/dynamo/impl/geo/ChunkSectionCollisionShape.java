package de.tomalbrc.dynamo.impl.geo;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public class ChunkSectionCollisionShape extends CompoundCollisionShape {
    private final SectionPos pos;

    static final int CHUNK_SIZE_X = 16;
    static final int CHUNK_SIZE_Z = 16;

    public ChunkSectionCollisionShape(LevelChunk chunk, SectionPos sectionPos) {
        this.pos = sectionPos;
        buildChunkCollisionShape(chunk);
    }

    public void buildChunkCollisionShape(LevelChunk chunk) {
        int baseX = pos.minBlockX();
        int baseZ = pos.minBlockZ();
        int minY = pos.minBlockY();
        int maxY = pos.maxBlockY()+1;
        int height = maxY - minY;
        if (height <= 0)
            return;

        // solidity [x][yi][z] (yi = y - minY)
        boolean[][][] solid = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
        for (int x = 0; x < CHUNK_SIZE_X; x++) {
            for (int yi = 0; yi < height; yi++) {
                for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                    tmp.set(baseX + x, minY + yi, baseZ + z);
                    BlockState st = chunk.getBlockState(tmp);
                    solid[x][yi][z] = !st.getCollisionShape(chunk, tmp).isEmpty();
                }
            }
        }

        // avoid duplicating voxels in multiple boxes
        boolean[][][] used = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];

        int boxesAdded = 0;

        // mask for greedy 2D packing per layer: mask[z][x]
        boolean[][] mask = new boolean[CHUNK_SIZE_Z][CHUNK_SIZE_X];

        // For each y we add axis-aligned rectangles on the xz plane
        for (int yi = 0; yi < height; yi++) {
            // mask cells that are solid and not used
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CHUNK_SIZE_X; x++) {
                    mask[z][x] = solid[x][yi][z] && !used[x][yi][z];
                }
            }

            // greedy rect packing on mask, rows=z cols=x
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CHUNK_SIZE_X; x++) {
                    if (!mask[z][x]) continue;

                    // find width along +X
                    int w = 1;
                    while (x + w < CHUNK_SIZE_X && mask[z][x + w]) w++;

                    // find height along +Z: the maximum number of rows we can extend
                    int h = 1;
                    outer:
                    while (z + h < CHUNK_SIZE_Z) {
                        for (int k = 0; k < w; k++) {
                            if (!mask[z + h][x + k]) break outer;
                        }
                        h++;
                    }

                    // Mark the mask cells consumed for this rectangle
                    for (int dz = 0; dz < h; dz++) {
                        for (int dx = 0; dx < w; dx++) {
                            mask[z + dz][x + dx] = false;
                        }
                    }

                    // Now we have a rectangle at layer yi: x in [x, x+w), z in [z, z+h)
                    int rectX0 = x;
                    int rectX1 = x + w; // exclusive
                    int rectZ0 = z;
                    int rectZ1 = z + h; // exclusive

                    // Try to grow this rectangle vertically (along Y) as far as possible
                    int boxHeight = 1;
                    vertical:
                    while (yi + boxHeight < height) {
                        for (int zz = rectZ0; zz < rectZ1; zz++) {
                            for (int xx = rectX0; xx < rectX1; xx++) {
                                if (!solid[xx][yi + boxHeight][zz] || used[xx][yi + boxHeight][zz]) {
                                    break vertical;
                                }
                            }
                        }
                        boxHeight++;
                    }

                    // Mark all voxels covered by the final box as used
                    for (int by = 0; by < boxHeight; by++) {
                        for (int zz = rectZ0; zz < rectZ1; zz++) {
                            for (int xx = rectX0; xx < rectX1; xx++) {
                                used[xx][yi + by][zz] = true;
                            }
                        }
                    }

                    // Create one BoxCollisionShape for that box
                    float sizeX = rectX1 - rectX0; // number of blocks in X
                    float sizeY = boxHeight;       // number of blocks in Y
                    float sizeZ = rectZ1 - rectZ0; // number of blocks in Z

                    // half extents (each block is 1 unit)
                    float hx = sizeX * 0.5f;
                    float hy = sizeY * 0.5f;
                    float hz = sizeZ * 0.5f;

                    // in world coordinates
                    float cx = baseX + rectX0 + hx;
                    float cy = minY + yi + hy;
                    float cz = baseZ + rectZ0 + hz;

                    BoxCollisionShape box = new BoxCollisionShape(new Vector3f(hx, hy, hz));
                    this.addChildShape(box, new Vector3f(cx, cy, cz));
                    boxesAdded++;
                }
            }
        }
    }
}


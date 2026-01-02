package de.tomalbrc.dynamo.impl.physics;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.math.Vector3f;
import com.jme3.util.BufferUtils;
import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ChunkSectionCollisionShape with merge-aware removal of fully-occluded voxels:
 * only keeps a removal if it does not increase (and preferably reduces) the box count.
 */
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
        int maxY = pos.maxBlockY() + 1;
        int height = maxY - minY;
        if (height <= 0) return;

        boolean[][][] solid = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
        for (int x = 0; x < CHUNK_SIZE_X; x++) {
            for (int yi = 0; yi < height; yi++) {
                for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                    tmp.set(baseX + x , minY + yi , baseZ + z );
                    BlockState st = chunk.getBlockState(tmp);
                    solid[x][yi][z] = !st.getCollisionShape(chunk, tmp).isEmpty();
                }
            }
        }

        var mesh = ChunkMeshGenerator.generateMesh(solid);
        if (mesh.vertices == null || mesh.vertices.length == 0)
            return;

        FloatBuffer v = BufferUtils.createFloatBuffer(mesh.vertices.length);
        for (int i = 0; i < mesh.vertices.length; i += 3) {
            v.put(mesh.vertices[(i)] + pos.getX() * 16);
            v.put(mesh.vertices[(i + 1)] + pos.getY() * 16);
            v.put(mesh.vertices[(i + 2)] + pos.getZ() * 16);
        }

        IndexedMesh indexedMesh = new IndexedMesh(v, BufferUtils.createIntBuffer(mesh.indices));
        this.addChildShape(new MeshCollisionShape(true, indexedMesh));

        if (true) return;

        List<int[]> candidates = new ArrayList<>();
        for (int x = 1; x < CHUNK_SIZE_X - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                for (int z = 1; z < CHUNK_SIZE_Z - 1; z++) {
                    if (!solid[x][y][z]) continue;
                    if (solid[x - 1][y][z] &&
                            solid[x + 1][y][z] &&
                            solid[x][y - 1][z] &&
                            solid[x][y + 1][z] &&
                            solid[x][y][z - 1] &&
                            solid[x][y][z + 1]) {
                        candidates.add(new int[]{x, y, z});
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            int baseline = packCount(solid, height);

            boolean removedAny;
            int totalRemoved = 0;

            do {
                removedAny = false;
                Iterator<int[]> it = candidates.iterator();
                while (it.hasNext()) {
                    int[] c = it.next();
                    int cx = c[0], cy = c[1], cz = c[2];

                    solid[cx][cy][cz] = false;

                    int newCount = packCount(solid, height);
                    if (newCount <= baseline) {
                        baseline = newCount;
                        totalRemoved++;
                        removedAny = true;
                        it.remove();
                    } else {
                        // revert
                        solid[cx][cy][cz] = true;
                    }
                }
            } while (removedAny && !candidates.isEmpty());

            if (totalRemoved > 0) {
                Dynamo.LOGGER.info("Merge-aware removed {} occluded voxels for section {} - updated boxCount: {}", totalRemoved, pos, baseline);
            }
        }

        boolean[][][] used = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];
        int boxesAdded = 0;
        boolean[][] mask = new boolean[CHUNK_SIZE_Z][CHUNK_SIZE_X];

        for (int yi = 0; yi < height; yi++) {
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CHUNK_SIZE_X; x++) {
                    mask[z][x] = solid[x][yi][z] && !used[x][yi][z];
                }
            }

            // greedy rect packing on mask, rows=z cols=x
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CHUNK_SIZE_X; x++) {
                    if (!mask[z][x]) continue;

                    // find width along x
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
                    int rectX1 = x + w;
                    int rectZ0 = z;
                    int rectZ1 = z + h;

                    // greedy grow vertically
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

                    for (int by = 0; by < boxHeight; by++) {
                        for (int zz = rectZ0; zz < rectZ1; zz++) {
                            for (int xx = rectX0; xx < rectX1; xx++) {
                                used[xx][yi + by][zz] = true;
                            }
                        }
                    }

                    float sizeX = rectX1 - rectX0;
                    float sizeY = boxHeight;
                    float sizeZ = rectZ1 - rectZ0;

                    float hx = sizeX * 0.5f;
                    float hy = sizeY * 0.5f;
                    float hz = sizeZ * 0.5f;

                    float cx = baseX + rectX0 + hx;
                    float cy = minY + yi + hy;
                    float cz = baseZ + rectZ0 + hz;

                    BoxCollisionShape box = new BoxCollisionShape(new Vector3f(hx, hy, hz));
                    this.addChildShape(box, new Vector3f(cx, cy, cz));
                    boxesAdded++;
                }
            }
        }

        Dynamo.LOGGER.info("Boxes added for section {}: {}", pos, boxesAdded);
    }

    private int packCount(boolean[][][] solid, int height) {
        boolean[][][] used = new boolean[CHUNK_SIZE_X][height][CHUNK_SIZE_Z];
        boolean[][] mask = new boolean[CHUNK_SIZE_Z][CHUNK_SIZE_X];
        int boxes = 0;

        for (int yi = 0; yi < height; yi++) {
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CHUNK_SIZE_X; x++) {
                    mask[z][x] = solid[x][yi][z] && !used[x][yi][z];
                }
            }

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
                    int rectX1 = x + w;
                    int rectZ0 = z;
                    int rectZ1 = z + h;

                    // greedy grow vertically
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

                    for (int by = 0; by < boxHeight; by++) {
                        for (int zz = rectZ0; zz < rectZ1; zz++) {
                            for (int xx = rectX0; xx < rectX1; xx++) {
                                used[xx][yi + by][zz] = true;
                            }
                        }
                    }

                    boxes++;
                }
            }
        }

        return boxes;
    }
}

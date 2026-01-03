package de.tomalbrc.dynamo.impl.physics;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.math.Vector3f;
import com.jme3.util.BufferUtils;
import com.llamalad7.mixinextras.sugar.Local;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.StlExporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

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

        boolean[][][] solid = new boolean[CHUNK_SIZE_X+2][height+2][CHUNK_SIZE_Z+2];
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
        for (int x = 0; x < CHUNK_SIZE_X+2; x++) {
            for (int yi = 0; yi < height+2; yi++) {
                for (int z = 0; z < CHUNK_SIZE_Z+2; z++) {
                    tmp.set(baseX + x-1, minY + yi-1, baseZ + z-1);
                    BlockState st = chunk.getBlockState(tmp);
                    solid[x][yi][z] = !st.getCollisionShape(chunk, tmp).isEmpty();
                }
            }
        }

        if (true) {
            var mesh = ChunkMeshGenerator.generateSmoothedMesh(solid);
            if (mesh.positions.isEmpty())
                return;

            var floatBuffer = BufferUtils.createFloatBuffer(mesh.positions.size());
            for (int i = 0; i < mesh.positions.size(); i+=3) {
                floatBuffer.put(mesh.positions.get(i)     + pos.x()*16);
                floatBuffer.put(mesh.positions.get(i+1)   + pos.y()*16);
                floatBuffer.put(mesh.positions.get(i+2)   + pos.z()*16);
            }
            IntBuffer intBuffer = BufferUtils.createIntBuffer(mesh.indices.stream().mapToInt(Integer::intValue).toArray());

            try {
                StlExporter.writeAsciiStl(String.format(Locale.US, "/tmp/section-%d-%d-%d.stl", pos.x(), pos.y(), pos.z()), "section", mesh.positions, mesh.indices);
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.addChildShape(new MeshCollisionShape(false, new IndexedMesh(floatBuffer, intBuffer)));

            return;
        }

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

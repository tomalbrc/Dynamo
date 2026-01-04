package de.tomalbrc.dynamo.impl.physics;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.math.Vector3f;
import com.jme3.util.BufferUtils;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.StlExporter;
import de.tomalbrc.dynamo.impl.MeshPos;
import de.tomalbrc.dynamo.impl.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class ChunkSectionCollisionShape extends CompoundCollisionShape {
    private final MeshPos pos;

    static final int CHUNK_SIZE = ModConfig.getInstance().chunkSize;

    CompletableFuture<MeshCollisionShape> smoothFuture;
    public MeshCollisionShape simpleShape;
    public boolean[][][] solid;

    public ChunkSectionCollisionShape(Level level, MeshPos meshPos) {
        this.pos = meshPos;
        this.buildChunkCollisionShape(level);
    }

    protected void buildMesh(boolean[][][] solid) {
        var mesh = ChunkMeshGenerator.generateSmoothedMesh(solid);
        final var shape = getMeshCollisionShape(mesh);
        if (shape == null) return;
        this.simpleShape = shape;
        this.addChildShape(shape);

        if (ModConfig.getInstance().exportMesh) {
            try {
                StlExporter.writeAsciiStl(String.format(Locale.US, "/tmp/section-%d-%d-%d.stl", pos.getX(), pos.getY(), pos.getZ()), "section", mesh.positions, mesh.indices);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private @Nullable MeshCollisionShape getMeshCollisionShape(ChunkMeshGenerator.MeshData mesh) {
        if (mesh.positions.isEmpty())
            return null;

        var floatBuffer = BufferUtils.createFloatBuffer(mesh.positions.size());
        for (int i = 0; i < mesh.positions.size(); i += 3) {
            floatBuffer.put(mesh.positions.get(i) +     pos.getX() * CHUNK_SIZE);
            floatBuffer.put(mesh.positions.get(i + 1) + pos.getY() * CHUNK_SIZE);
            floatBuffer.put(mesh.positions.get(i + 2) + pos.getZ() * CHUNK_SIZE);

            mesh.positions.set(i, floatBuffer.get(i));
            mesh.positions.set(i + 1, floatBuffer.get(i+1) + 1.f);
            mesh.positions.set(i + 2, floatBuffer.get(i+2));
        }
        IntBuffer intBuffer = BufferUtils.createIntBuffer(mesh.indices.stream().mapToInt(Integer::intValue).toArray());

        return new MeshCollisionShape(false, new IndexedMesh(floatBuffer, intBuffer));
    }

    public void buildChunkCollisionShape(Level level) {
        int baseX = pos.minBlockX();
        int baseZ = pos.minBlockZ();
        int minY = pos.minBlockY();
        int maxY = pos.maxBlockY() + 1;
        int height = maxY - minY;
        assert height == CHUNK_SIZE;

        boolean mesh = ModConfig.getInstance().mesh;

        var additionalRad = mesh? 4 : 0;
        var additionalRadHalf = mesh? additionalRad/2 : 0;

        boolean hasSolid = this.solid != null;
        if (this.solid == null) {
            this.solid = new boolean[CHUNK_SIZE + additionalRad][CHUNK_SIZE + additionalRad][CHUNK_SIZE + additionalRad];
            BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
            for (int x = 0; x < CHUNK_SIZE + additionalRad; x++) {
                for (int y = 0; y < CHUNK_SIZE + additionalRad; y++) {
                    for (int z = 0; z < CHUNK_SIZE + additionalRad; z++) {
                        tmp.set((baseX + x) - additionalRadHalf, (minY + y) - additionalRadHalf, (baseZ + z) - additionalRadHalf);
                        BlockState st = level.getBlockState(tmp);
                        boolean isSolid = !st.is(BlockTags.LEAVES) && !st.getCollisionShape(level, tmp).isEmpty();
                        this.solid[x][y][z] = isSolid;
                        hasSolid |= isSolid;
                    }
                }
            }
        }

        if (!hasSolid)
            return;

        if (mesh) {
            buildMesh(solid);
            return;
        }

        List<int[]> candidates = new ArrayList<>();
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
                for (int z = 1; z < CHUNK_SIZE - 1; z++) {
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
            int baseline = packCount(solid);

            boolean removedAny;
            int totalRemoved = 0;

            do {
                removedAny = false;
                Iterator<int[]> it = candidates.iterator();
                while (it.hasNext()) {
                    int[] c = it.next();
                    int cx = c[0], cy = c[1], cz = c[2];

                    solid[cx][cy][cz] = false;

                    int newCount = packCount(solid);
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

        boolean[][][] used = new boolean[CHUNK_SIZE][CHUNK_SIZE][CHUNK_SIZE];
        int boxesAdded = 0;
        boolean[][] mask = new boolean[CHUNK_SIZE][CHUNK_SIZE];

        for (int yi = 0; yi < CHUNK_SIZE; yi++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    mask[z][x] = solid[x][yi][z] && !used[x][yi][z];
                }
            }

            // greedy rect packing on mask, rows=z cols=x
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    if (!mask[z][x]) continue;

                    // find width along x
                    int w = 1;
                    while (x + w < CHUNK_SIZE && mask[z][x + w]) w++;

                    int h = 1;
                    outer:
                    while (z + h < CHUNK_SIZE) {
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
                    while (yi + boxHeight < CHUNK_SIZE) {
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

    private int packCount(boolean[][][] solid) {
        boolean[][][] used = new boolean[CHUNK_SIZE][CHUNK_SIZE][CHUNK_SIZE];
        boolean[][] mask = new boolean[CHUNK_SIZE][CHUNK_SIZE];
        int boxes = 0;

        for (int yi = 0; yi < CHUNK_SIZE; yi++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    mask[z][x] = solid[x][yi][z] && !used[x][yi][z];
                }
            }

            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    if (!mask[z][x]) continue;

                    int w = 1;
                    while (x + w < CHUNK_SIZE && mask[z][x + w]) w++;

                    int h = 1;
                    outer:
                    while (z + h < CHUNK_SIZE) {
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
                    while (yi + boxHeight < CHUNK_SIZE) {
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

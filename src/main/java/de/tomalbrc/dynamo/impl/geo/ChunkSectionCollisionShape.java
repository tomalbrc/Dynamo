package de.tomalbrc.dynamo.impl.geo;

import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.math.Vector3f;
import com.jme3.util.BufferUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public class ChunkSectionCollisionShape extends MeshCollisionShape {
    static final int SIZE = 16;

    public static ChunkSectionCollisionShape shape(LevelChunk chunk, SectionPos sectionPos) {
        IndexedMesh mesh = createGreedyMesh(chunk, sectionPos);
        return mesh != null ? new ChunkSectionCollisionShape(mesh) : null;
    }

    private ChunkSectionCollisionShape(IndexedMesh mesh) {
        super(true, mesh);
    }

    private static IndexedMesh createGreedyMesh(LevelChunk chunk, SectionPos sectionPos) {
        int baseX = sectionPos.minBlockX();
        int baseY = sectionPos.minBlockY();
        int baseZ = sectionPos.minBlockZ();

        boolean[][][] solid = new boolean[SIZE][SIZE][SIZE];
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
        boolean hasAny = false;

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    tmp.set(baseX + x, baseY + y, baseZ + z);
                    BlockState st = chunk.getBlockState(tmp);
                    boolean isSolid = !st.getCollisionShape(chunk, tmp).isEmpty();
                    solid[x][y][z] = isSolid;
                    hasAny |= isSolid;
                }
            }
        }

        if (!hasAny) return null;

        List<Float> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // Sweep over 3 axes (d=0:X, d=1:Y, d=2:Z)
        for (int d = 0; d < 3; d++) {
            int u = (d + 1) % 3; // axis 1
            int v = (d + 2) % 3; // axis 2
            int[] x = new int[3];
            int[] q = new int[3];
            q[d] = 1;

            // Mask contains direction of the face (-1 or 1)
            int[] mask = new int[SIZE * SIZE];

            for (x[d] = -1; x[d] < SIZE; ) {
                int n = 0;
                // Compute mask for this slice
                for (x[v] = 0; x[v] < SIZE; x[v]++) {
                    for (x[u] = 0; x[u] < SIZE; x[u]++) {
                        boolean blockCurrent = (x[d] >= 0) && solid[x[0]][x[1]][x[2]];
                        boolean blockNeighbor = (x[d] < SIZE - 1) && solid[x[0] + q[0]][x[1] + q[1]][x[2] + q[2]];

                        if (blockCurrent == blockNeighbor) {
                            mask[n++] = 0;
                        } else {
                            mask[n++] = blockCurrent ? 1 : -1;
                        }
                    }
                }

                x[d]++;
                n = 0;
                // Generate quads from mask
                for (int j = 0; j < SIZE; j++) {
                    for (int i = 0; i < SIZE; ) {
                        int type = mask[n];
                        if (type != 0) {
                            int w, h;
                            // Calculate width
                            for (w = 1; i + w < SIZE && mask[n + w] == type; w++) ;

                            // Calculate height
                            boolean done = false;
                            for (h = 1; j + h < SIZE; h++) {
                                for (int k = 0; k < w; k++) {
                                    if (mask[n + k + h * SIZE] != type) {
                                        done = true; break;
                                    }
                                }
                                if (done) break;
                            }

                            // Add Quad
                            x[u] = i; x[v] = j;
                            int[] du = new int[3]; du[u] = w;
                            int[] dv = new int[3]; dv[v] = h;

                            addQuad(verts, indices,
                                    new float[]{baseX + x[0], baseY + x[1], baseZ + x[2]},
                                    new float[]{du[0], du[1], du[2]},
                                    new float[]{dv[0], dv[1], dv[2]},
                                    type > 0);

                            // Clear mask for this quad
                            for (int l = 0; l < h; l++) {
                                for (int k = 0; k < w; k++) {
                                    mask[n + k + l * SIZE] = 0;
                                }
                            }
                            i += w; n += w;
                        } else {
                            i++; n++;
                        }
                    }
                }
            }
        }

        return buildIndexedMesh(verts, indices);
    }

    private static void addQuad(List<Float> verts, List<Integer> indices, float[] pos, float[] du, float[] dv, boolean backFace) {
        int offset = verts.size() / 3;

        // 4 Vertices
        float[][] v = {
                {pos[0], pos[1], pos[2]},
                {pos[0] + du[0], pos[1] + du[1], pos[2] + du[2]},
                {pos[0] + du[0] + dv[0], pos[1] + du[1] + dv[1], pos[2] + du[2] + dv[2]},
                {pos[0] + dv[0], pos[1] + dv[1], pos[2] + dv[2]}
        };

        for (float[] vertex : v) {
            verts.add(vertex[0]); verts.add(vertex[1]); verts.add(vertex[2]);
        }

        if (backFace) {
            indices.add(offset); indices.add(offset + 2); indices.add(offset + 1);
            indices.add(offset); indices.add(offset + 3); indices.add(offset + 2);
        } else {
            indices.add(offset); indices.add(offset + 1); indices.add(offset + 2);
            indices.add(offset); indices.add(offset + 2); indices.add(offset + 3);
        }
    }

    private static IndexedMesh buildIndexedMesh(List<Float> verts, List<Integer> indices) {
        float[] vArray = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) vArray[i] = verts.get(i);

        int[] iArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) iArray[i] = indices.get(i);

        return new IndexedMesh(BufferUtils.createFloatBuffer(vArray), BufferUtils.createIntBuffer(iArray));
    }
}
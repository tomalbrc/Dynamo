package de.tomalbrc.dynamo.impl.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Greedy meshing for a 16x16x16 boolean chunk with:
 *  - per-plane greedy rectangle tiling
 *  - vertex deduplication with index buffer
 *  - option to expand into non-indexed triangles (if needed)
 *
 * This reduces vertex duplication and avoids T-junctions by building
 * consistent quads on integer grid lines per plane and sharing vertex positions.
 */
public class ChunkMeshGenerator {

    public static class MeshData {
        // positions as x,y,z floats (3 floats per unique vertex)
        public final List<Float> positions = new ArrayList<>();
        // triangles are indices into positions (3 ints per triangle)
        public final List<Integer> indices = new ArrayList<>();
    }

    private static long encodeKey(int x, int y, int z) {
        return (((long) x & 0xFFFFFL) << 40) | (((long) y & 0xFFFFFL) << 20) | ((long) z & 0xFFFFFL);
    }

    public static MeshData generateMesh(boolean[][][] blocks) {
        final MeshData mesh = new MeshData();

        final int sizeX = 16, sizeY = 16, sizeZ = 16;
        final int[] dims = new int[]{sizeX, sizeY, sizeZ};

        // vertex dedupe structures
        final Map<Long, Integer> vertexIndex = new HashMap<>(); // encoded pos -> index
        final List<Float> positions = mesh.positions;
        final List<Integer> indices = mesh.indices;

        // For each axis (d), for each plane i, we build masks and greedily tile them into rectangles.
        for (int d = 0; d < 3; d++) {
            final int u = (d + 1) % 3;
            final int v = (d + 2) % 3;

            int[] x = new int[3];

            for (int i = 0; i <= dims[d]; i++) {
                // Build mask for this plane (values -1, 0, 1)
                int[][] mask = new int[dims[u]][dims[v]];
                for (int j = 0; j < dims[u]; j++) {
                    for (int k = 0; k < dims[v]; k++) {
                        x[d] = i - 1;
                        x[u] = j;
                        x[v] = k;
                        boolean a = inBounds(x, dims) && blocks[x[0]][x[1]][x[2]];

                        x[d] = i;
                        boolean b = inBounds(x, dims) && blocks[x[0]][x[1]][x[2]];

                        if (a == b) {
                            mask[j][k] = 0;
                        } else if (a && !b) {
                            mask[j][k] = 1;
                        } else { // !a && b
                            mask[j][k] = -1;
                        }
                    }
                }

                // For *each sign* separately, run the greedy rectangle packing on the integer grid.
                for (int sign : new int[]{1, -1}) {
                    boolean[][] used = new boolean[dims[u]][dims[v]];

                    for (int j = 0; j < dims[u]; j++) {
                        for (int k = 0; k < dims[v]; k++) {
                            if (used[j][k]) continue;
                            if (mask[j][k] != sign) continue;

                            // find width w
                            int w;
                            for (w = 1; k + w < dims[v] && mask[j][k + w] == sign && !used[j][k + w]; w++) {}

                            // find height h while ensuring each row has same contiguous run
                            int h;
                            outer:
                            for (h = 1; j + h < dims[u]; h++) {
                                for (int n = 0; n < w; n++) {
                                    if (mask[j + h][k + n] != sign || used[j + h][k + n]) break outer;
                                }
                            }

                            // mark used
                            for (int jj = 0; jj < h; jj++) {
                                for (int kk = 0; kk < w; kk++) {
                                    used[j + jj][k + kk] = true;
                                }
                            }

                            // Now we have a rectangle at (u=j..j+h, v=k..k+w) in plane i with 'sign'
                            // Compute four corner coordinates (integer grid positions)
                            int[] c0 = new int[3];
                            int[] c1 = new int[3];
                            int[] c2 = new int[3];
                            int[] c3 = new int[3];

                            c0[d] = i;
                            c1[d] = i;
                            c2[d] = i;
                            c3[d] = i;

                            c0[u] = j;
                            c0[v] = k;

                            c1[u] = j + h;
                            c1[v] = k;

                            c2[u] = j + h;
                            c2[v] = k + w;

                            c3[u] = j;
                            c3[v] = k + w;

                            // add/lookup vertices (deduplicate by position)
                            int idx0 = getOrCreateVertexIndex(vertexIndex, positions, c0[0], c0[1], c0[2]);
                            int idx1 = getOrCreateVertexIndex(vertexIndex, positions, c1[0], c1[1], c1[2]);
                            int idx2 = getOrCreateVertexIndex(vertexIndex, positions, c2[0], c2[1], c2[2]);
                            int idx3 = getOrCreateVertexIndex(vertexIndex, positions, c3[0], c3[1], c3[2]);

                            // Emit two triangles (indices) with original winding depending on sign
                            if (sign == 1) {
                                // c0, c1, c2 and c0, c2, c3
                                indices.add(idx0);
                                indices.add(idx1);
                                indices.add(idx2);

                                indices.add(idx0);
                                indices.add(idx2);
                                indices.add(idx3);
                            } else {
                                // c0, c2, c1 and c0, c3, c2
                                indices.add(idx0);
                                indices.add(idx2);
                                indices.add(idx1);

                                indices.add(idx0);
                                indices.add(idx3);
                                indices.add(idx2);
                            }
                        }
                    }
                }
            }
        }

        return mesh;
    }

    private static int getOrCreateVertexIndex(Map<Long, Integer> vertexIndex, List<Float> positions, int x, int y, int z) {
        long key = encodeKey(x, y, z);
        Integer idx = vertexIndex.get(key);
        if (idx != null) return idx;

        idx = positions.size() / 3;
        positions.add((float) x);
        positions.add((float) y);
        positions.add((float) z);
        vertexIndex.put(key, idx);
        return idx;
    }

    private static boolean inBounds(int[] x, int[] dims) {
        return x[0] >= 0 && x[0] < dims[0] && x[1] >= 0 && x[1] < dims[1] && x[2] >= 0 && x[2] < dims[2];
    }
}

package de.tomalbrc.dynamo.impl.physics;

import java.util.*;

public class ChunkMeshGenerator {

    public static class MeshData {
        public float[] vertices;
        public int[] indices;
    }

    public static MeshData generateMesh(boolean[][][] blocks) {
        int size = 16;
        // Map to store vertex indices based on grid position (x,y,z)
        // We use a long key to store "x,y,z" efficiently
        Map<Integer, Integer> vertexMap = new HashMap<>();
        List<Float> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // 1. Generate Faces (No Greedy Meshing to allow smoothing)
        for (int x = 0; x <= size; x++) {
            for (int y = 0; y <= size; y++) {
                for (int z = 0; z <= size; z++) {
                    for (int d = 0; d < 3; d++) {
                        boolean a = getBlock(blocks, x - (d==0?1:0), y - (d==1?1:0), z - (d==2?1:0));
                        boolean b = getBlock(blocks, x, y, z);

                        if (a != b) {
                            // We found a surface face!
                            // Add 4 corners of the 1x1 face
                            int[] quad = new int[4];
                            quad[0] = getOrCreateVertex(x, y, z, blocks, verts, vertexMap);

                            // Define quad corners based on axis d
                            if (d == 0) { // X-axis face
                                quad[1] = getOrCreateVertex(x, y + 1, z, blocks, verts, vertexMap);
                                quad[2] = getOrCreateVertex(x, y + 1, z + 1, blocks, verts, vertexMap);
                                quad[3] = getOrCreateVertex(x, y, z + 1, blocks, verts, vertexMap);
                            } else if (d == 1) { // Y-axis face
                                quad[1] = getOrCreateVertex(x + 1, y, z, blocks, verts, vertexMap);
                                quad[2] = getOrCreateVertex(x + 1, y, z + 1, blocks, verts, vertexMap);
                                quad[3] = getOrCreateVertex(x, y, z + 1, blocks, verts, vertexMap);
                            } else { // Z-axis face
                                quad[1] = getOrCreateVertex(x + 1, y, z, blocks, verts, vertexMap);
                                quad[2] = getOrCreateVertex(x + 1, y + 1, z, blocks, verts, vertexMap);
                                quad[3] = getOrCreateVertex(x, y + 1, z, blocks, verts, vertexMap);
                            }

                            // Add indices (Winding order depends on a vs b)
                            if (a) {
                                appendQuad(indices, quad[0], quad[1], quad[2], quad[3]);
                            } else {
                                appendQuad(indices, quad[0], quad[3], quad[2], quad[1]);
                            }
                        }
                    }
                }
            }
        }

        MeshData mesh = new MeshData();
        mesh.vertices = new float[verts.size()];
        for(int i=0; i<verts.size(); i++) mesh.vertices[i] = verts.get(i);
        mesh.indices = new int[indices.size()];
        for(int i=0; i<indices.size(); i++) mesh.indices[i] = indices.get(i);
        return mesh;
    }

    private static int getOrCreateVertex(int x, int y, int z, boolean[][][] blocks, List<Float> verts, Map<Integer, Integer> map) {
        int key = (x << 20) | (y << 10) | z;
        if (map.containsKey(key)) return map.get(key);

        // --- SMOOTHING LOGIC ---
        // Instead of returning (x, y, z), we find the "centroid" of the empty space
        // surrounding this corner. This is a basic "Dual Contouring" approach.
        float sx = x, sy = y, sz = z;

        // Sample the 8 voxels surrounding this corner point
        float count = 0;
        float avgX = 0, avgY = 0, avgZ = 0;

        for(int dx = -1; dx <= 0; dx++) {
            for(int dy = -1; dy <= 0; dy++) {
                for(int dz = -1; dz <= 0; dz++) {
                    if (getBlock(blocks, x + dx, y + dy, z + dz)) {
                        avgX += (x + dx + 0.5f);
                        avgY += (y + dy + 0.5f);
                        avgZ += (z + dz + 0.5f);
                        count++;
                    }
                }
            }
        }

        if (count > 0 && count < 8) {
            // Nudge the vertex toward the center of the solid mass
            // This "rounds" the sharp corners.
            float weight = 1.f; // Increase for more smoothing
            sx = sx * (1 - weight) + (avgX / count) * weight;
            sy = sy * (1 - weight) + (avgY / count) * weight;
            sz = sz * (1 - weight) + (avgZ / count) * weight;
        }

        int index = verts.size() / 3;
        verts.add(sx); verts.add(sy); verts.add(sz);
        map.put(key, index);
        return index;
    }

    private static void appendQuad(List<Integer> indices, int v1, int v2, int v3, int v4) {
        indices.add(v1); indices.add(v2); indices.add(v3);
        indices.add(v1); indices.add(v3); indices.add(v4);
    }

    private static boolean getBlock(boolean[][][] blocks, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= 16 || y >= 16 || z >= 16) return false;
        return blocks[x][y][z];
    }
}
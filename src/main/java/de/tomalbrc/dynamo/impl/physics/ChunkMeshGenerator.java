package de.tomalbrc.dynamo.impl.physics;

import java.util.ArrayList;
import java.util.List;

public class ChunkMeshGenerator {
    public static class MeshData {
        public final List<Float> vertices = new ArrayList<>();
        public final List<Float> normals  = new ArrayList<>();
        public final List<Float> uvs      = new ArrayList<>();
    }

    // Simple voxel array: 0=air, >0=solid
    public static MeshData generateMesh(byte[][][] blocks) {
        MeshData mesh = new MeshData();
        int sizeX = 16, sizeY = 16, sizeZ = 16;

        // Offsets for all 6 cube faces
        int[][] faceDirs = {
            { 1, 0, 0}, {-1, 0, 0},
            { 0, 1, 0}, { 0,-1, 0},
            { 0, 0, 1}, { 0, 0,-1}
        };

        // Loop all blocks
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (blocks[x][y][z] == 0) continue; // skip air

                    for (int f = 0; f < faceDirs.length; f++) {
                        int nx = x + faceDirs[f][0];
                        int ny = y + faceDirs[f][1];
                        int nz = z + faceDirs[f][2];

                        boolean visible = true;
                        if (nx >= 0 && ny >= 0 && nz >= 0
                         && nx < sizeX && ny < sizeY && nz < sizeZ) {
                            visible = (blocks[nx][ny][nz] == 0);
                        }

                        if (!visible) continue;

                        addFace(mesh, x, y, z, f);
                    }
                }
            }
        }
        return mesh;
    }

    // Add a face (quad) for block at (x,y,z) in direction `faceIdx`
    private static void addFace(MeshData mesh, int x, int y, int z, int faceIdx) {
        // Simple face definitions
        float[][] FACE_VERTS = {
            {1,0,0, 1,1,0, 1,1,1, 1,0,1},  // +X
            {0,0,1, 0,1,1, 0,1,0, 0,0,0},  // -X
            {0,1,1, 1,1,1, 1,1,0, 0,1,0},  // +Y
            {0,0,0, 1,0,0, 1,0,1, 0,0,1},  // -Y
            {0,0,1, 1,0,1, 1,1,1, 0,1,1},  // +Z
            {1,0,0, 0,0,0, 0,1,0, 1,1,0}   // -Z
        };

        float[] normal = {
            faceIdx == 0 ? 1 : faceIdx == 1 ? -1 : 0,
            faceIdx == 2 ? 1 : faceIdx == 3 ? -1 : 0,
            faceIdx == 4 ? 1 : faceIdx == 5 ? -1 : 0
        };

        int base = faceIdx * 12;
        for (int v = 0; v < 4; v++) {
            float vx = x + FACE_VERTS[faceIdx][v*3 + 0];
            float vy = y + FACE_VERTS[faceIdx][v*3 + 1];
            float vz = z + FACE_VERTS[faceIdx][v*3 + 2];
            mesh.vertices.add(vx);
            mesh.vertices.add(vy);
            mesh.vertices.add(vz);

            mesh.normals.add(normal[0]);
            mesh.normals.add(normal[1]);
            mesh.normals.add(normal[2]);

            // Simple UVs: (0,0),(1,0),(1,1),(0,1)
            mesh.uvs.add(v == 0 || v == 3 ? 0f : 1f);
            mesh.uvs.add(v < 2 ? 0f : 1f);
        }
    }
}

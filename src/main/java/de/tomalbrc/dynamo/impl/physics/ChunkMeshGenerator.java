package de.tomalbrc.dynamo.impl.physics;

import java.util.ArrayList;
import java.util.List;

public class ChunkMeshGenerator {

    public static class MeshData {
        public final List<Float> vertices = new ArrayList<>();
        public final List<Float> normals  = new ArrayList<>();
        public final List<Float> uvs      = new ArrayList<>();
    }

    // true = solid, false = air
    public static MeshData generateMesh(boolean[][][] blocks) {
        MeshData mesh = new MeshData();
        int sizeX = 16, sizeY = 16, sizeZ = 16;

        int[][] faceDirs = {
                { 1, 0, 0}, {-1, 0, 0},
                { 0, 1, 0}, { 0,-1, 0},
                { 0, 0, 1}, { 0, 0,-1}
        };

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (!blocks[x][y][z]) continue;

                    for (int f = 0; f < 6; f++) {
                        int nx = x + faceDirs[f][0];
                        int ny = y + faceDirs[f][1];
                        int nz = z + faceDirs[f][2];

                        boolean visible = true;
                        if (nx >= 0 && ny >= 0 && nz >= 0
                                && nx < sizeX && ny < sizeY && nz < sizeZ) {
                            visible = !blocks[nx][ny][nz];
                        }

                        if (visible) {
                            addFaceTriangles(mesh, x, y, z, f);
                        }
                    }
                }
            }
        }
        return mesh;
    }

    // Emits 2 triangles (6 vertices) per face
    private static void addFaceTriangles(MeshData mesh, int x, int y, int z, int faceIdx) {

        // 4 corner vertices per face (quad)
        float[][] FACE_VERTS = {
                {1,0,0, 1,1,0, 1,1,1, 1,0,1}, // +X
                {0,0,1, 0,1,1, 0,1,0, 0,0,0}, // -X
                {0,1,1, 1,1,1, 1,1,0, 0,1,0}, // +Y
                {0,0,0, 1,0,0, 1,0,1, 0,0,1}, // -Y
                {0,0,1, 1,0,1, 1,1,1, 0,1,1}, // +Z
                {1,0,0, 0,0,0, 0,1,0, 1,1,0}  // -Z
        };

        int[] TRI_ORDER = {
                0, 1, 2, // triangle 1
                0, 2, 3  // triangle 2
        };

        float nx =
                faceIdx == 0 ?  1 :
                        faceIdx == 1 ? -1 : 0;
        float ny =
                faceIdx == 2 ?  1 :
                        faceIdx == 3 ? -1 : 0;
        float nz =
                faceIdx == 4 ?  1 :
                        faceIdx == 5 ? -1 : 0;

        float[] verts = FACE_VERTS[faceIdx];

        for (int i = 0; i < TRI_ORDER.length; i++) {
            int v = TRI_ORDER[i] * 3;

            mesh.vertices.add(x + verts[v]);
            mesh.vertices.add(y + verts[v + 1]);
            mesh.vertices.add(z + verts[v + 2]);

            mesh.normals.add(nx);
            mesh.normals.add(ny);
            mesh.normals.add(nz);

            // Basic quad UVs, duplicated per triangle
            mesh.uvs.add((TRI_ORDER[i] == 0 || TRI_ORDER[i] == 3) ? 0f : 1f);
            mesh.uvs.add(TRI_ORDER[i] < 2 ? 0f : 1f);
        }
    }
}

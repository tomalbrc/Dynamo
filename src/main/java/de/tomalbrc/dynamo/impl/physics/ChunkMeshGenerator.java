package de.tomalbrc.dynamo.impl.physics;

import java.util.ArrayList;
import java.util.List;

public class ChunkMeshGenerator {
    public static class MeshData {
        public final List<Float> vertices = new ArrayList<>();
    }

    public static MeshData generateMesh(boolean[][][] blocks) {
        MeshData mesh = new MeshData();
        int sizeX = 16, sizeY = 16, sizeZ = 16;

        int[][] faceDirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (!blocks[x][y][z]) continue;

                    for (int f = 0; f < 6; f++) {
                        int nx = x + faceDirs[f][0];
                        int ny = y + faceDirs[f][1];
                        int nz = z + faceDirs[f][2];

                        boolean visible = true;
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sizeX && ny < sizeY && nz < sizeZ) {
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

    private static void addFaceTriangles(MeshData mesh, int x, int y, int z, int faceIdx) {
        float[][] FACE_VERTS = {
                {1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1}, // +X
                {0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0}, // -X
                {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, // +Y
                {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, // -Y
                {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}, // +Z
                {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0}  // -Z
        };

        int[] TRI_ORDER = {
                0, 1, 2,
                0, 2, 3
        };

        float[] verts = FACE_VERTS[faceIdx];

        for (int i = 0; i < TRI_ORDER.length; i++) {
            int v = TRI_ORDER[i] * 3;

            mesh.vertices.add(x + verts[v]);
            mesh.vertices.add(y + verts[v + 1]);
            mesh.vertices.add(z + verts[v + 2]);
        }
    }
}

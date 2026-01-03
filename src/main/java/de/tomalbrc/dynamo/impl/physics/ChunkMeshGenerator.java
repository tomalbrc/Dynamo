package de.tomalbrc.dynamo.impl.physics;

import java.util.ArrayList;
import java.util.List;

public class ChunkMeshGenerator {
    public static class MeshData {
        public final List<Float> positions = new ArrayList<>();
        public final List<Integer> indices = new ArrayList<>();
        public final List<Float> normals = new ArrayList<>();
    }

    public static MeshData generateMesh(boolean[][][] blocks) {
        MeshData data = new MeshData();
        var len = blocks.length;
        for (int x = 1; x <= len; x++) {
            for (int y = 1; y <= len; y++) {
                for (int z = 1; z <= len; z++) {
                    if (blocks[x][y][z]) {
                        generateBlockFaces(blocks, x, y, z, data);
                    }
                }
            }
        }

        return data;
    }

    private static void generateBlockFaces(boolean[][][] blocks, int x, int y, int z, MeshData data) {
        int baseIndex = data.positions.size() / 3;

        float[][] cubeVertices = {
                {x, y, z}, {x+1, y, z}, {x+1, y, z+1}, {x, y, z+1},  // Bottom face
                {x, y+1, z}, {x+1, y+1, z}, {x+1, y+1, z+1}, {x, y+1, z+1}  // Top face
        };

        int[][] faceIndices = {
                {0, 1, 2, 3},  // Bottom
                {4, 5, 6, 7},  // Top
                {0, 1, 5, 4},  // Front
                {2, 3, 7, 6},  // Back
                {1, 2, 6, 5},  // Right
                {0, 3, 7, 4}   // Left
        };

        float[][] faceNormals = {
                {0, -1, 0},  // Bottom
                {0, 1, 0},   // Top
                {0, 0, -1},  // Front
                {0, 0, 1},   // Back
                {1, 0, 0},   // Right
                {-1, 0, 0}   // Left
        };

        int[][] neighborOffsets = {
                {0, -1, 0},  // Bottom
                {0, 1, 0},   // Top
                {0, 0, -1},  // Front
                {0, 0, 1},   // Back
                {1, 0, 0},   // Right
                {-1, 0, 0}   // Left
        };

        for (int face = 0; face < 6; face++) {
            int nx = x + neighborOffsets[face][0];
            int ny = y + neighborOffsets[face][1];
            int nz = z + neighborOffsets[face][2];

            if (!blocks[nx][ny][nz]) {
                for (int i = 0; i < 4; i++) {
                    int vertexIdx = faceIndices[face][i];
                    data.positions.add(cubeVertices[vertexIdx][0]);
                    data.positions.add(cubeVertices[vertexIdx][1]);
                    data.positions.add(cubeVertices[vertexIdx][2]);

                    data.normals.add(faceNormals[face][0]);
                    data.normals.add(faceNormals[face][1]);
                    data.normals.add(faceNormals[face][2]);
                }

                data.indices.add(baseIndex);
                data.indices.add(baseIndex + 1);
                data.indices.add(baseIndex + 2);
                data.indices.add(baseIndex);
                data.indices.add(baseIndex + 2);
                data.indices.add(baseIndex + 3);

                baseIndex += 4;
            }
        }
    }

    public static MeshData generateSmoothedMesh(boolean[][][] blocks) {
        MeshData data = new MeshData();

        float[][][] density = new float[18][18][18];
        for (int x = 0; x < 18; x++) {
            for (int y = 0; y < 18; y++) {
                for (int z = 0; z < 18; z++) {
                    density[x][y][z] = blocks[x][y][z] ? 1.0f : 0.0f;
                }
            }
        }

        smoothDensityField(density);

        float threshold = 0.2f;

        for (int x = 1; x <= 16; x++) {
            for (int y = 1; y <= 16; y++) {
                for (int z = 1; z <= 16; z++) {
                    processMarchingCubeWithEdgeTable(x, y, z, density, threshold, data);
                    processMarchingCubeWithEdgeTable(x, y, z, density, threshold, data);
                }
            }
        }

        return data;
    }

    private static void processMarchingCubeWithEdgeTable(int x, int y, int z, float[][][] density,
                                                         float threshold, MeshData data) {
        float[] cubeDensity = new float[8];
        cubeDensity[0] = density[x][y][z];
        cubeDensity[1] = density[x+1][y][z];
        cubeDensity[2] = density[x+1][y][z+1];
        cubeDensity[3] = density[x][y][z+1];
        cubeDensity[4] = density[x][y+1][z];
        cubeDensity[5] = density[x+1][y+1][z];
        cubeDensity[6] = density[x+1][y+1][z+1];
        cubeDensity[7] = density[x][y+1][z+1];

        // Calculate cube index using bitmask
        int cubeIndex = 0;
        if (cubeDensity[0] < threshold) cubeIndex |= 1;
        if (cubeDensity[1] < threshold) cubeIndex |= 2;
        if (cubeDensity[2] < threshold) cubeIndex |= 4;
        if (cubeDensity[3] < threshold) cubeIndex |= 8;
        if (cubeDensity[4] < threshold) cubeIndex |= 16;
        if (cubeDensity[5] < threshold) cubeIndex |= 32;
        if (cubeDensity[6] < threshold) cubeIndex |= 64;
        if (cubeDensity[7] < threshold) cubeIndex |= 128;

        int edgeMask = EDGE_TABLE[cubeIndex];

        if (edgeMask == 0) {
            return;
        }

        float[][] edgeVertices = new float[12][];

        if ((edgeMask & 1) != 0) {
            edgeVertices[0] = interpolateVertex(x, y, z, x+1, y, z,
                    cubeDensity[0], cubeDensity[1], threshold);
        }
        if ((edgeMask & 2) != 0) {
            edgeVertices[1] = interpolateVertex(x+1, y, z, x+1, y, z+1,
                    cubeDensity[1], cubeDensity[2], threshold);
        }
        if ((edgeMask & 4) != 0) {
            edgeVertices[2] = interpolateVertex(x+1, y, z+1, x, y, z+1,
                    cubeDensity[2], cubeDensity[3], threshold);
        }
        if ((edgeMask & 8) != 0) {
            edgeVertices[3] = interpolateVertex(x, y, z, x, y, z+1,
                    cubeDensity[0], cubeDensity[3], threshold);
        }
        if ((edgeMask & 16) != 0) {
            edgeVertices[4] = interpolateVertex(x, y+1, z, x+1, y+1, z,
                    cubeDensity[4], cubeDensity[5], threshold);
        }
        if ((edgeMask & 32) != 0) {
            edgeVertices[5] = interpolateVertex(x+1, y+1, z, x+1, y+1, z+1,
                    cubeDensity[5], cubeDensity[6], threshold);
        }
        if ((edgeMask & 64) != 0) {
            edgeVertices[6] = interpolateVertex(x+1, y+1, z+1, x, y+1, z+1,
                    cubeDensity[6], cubeDensity[7], threshold);
        }
        if ((edgeMask & 128) != 0) {
            edgeVertices[7] = interpolateVertex(x, y+1, z, x, y+1, z+1,
                    cubeDensity[4], cubeDensity[7], threshold);
        }
        if ((edgeMask & 256) != 0) {
            edgeVertices[8] = interpolateVertex(x, y, z, x, y+1, z,
                    cubeDensity[0], cubeDensity[4], threshold);
        }
        if ((edgeMask & 512) != 0) {
            edgeVertices[9] = interpolateVertex(x+1, y, z, x+1, y+1, z,
                    cubeDensity[1], cubeDensity[5], threshold);
        }
        if ((edgeMask & 1024) != 0) {
            edgeVertices[10] = interpolateVertex(x+1, y, z+1, x+1, y+1, z+1,
                    cubeDensity[2], cubeDensity[6], threshold);
        }
        if ((edgeMask & 2048) != 0) {
            edgeVertices[11] = interpolateVertex(x, y, z+1, x, y+1, z+1,
                    cubeDensity[3], cubeDensity[7], threshold);
        }

        int[] triangleEdges = TRIANGLE_TABLE[cubeIndex];

        for (int i = 0; i < triangleEdges.length && triangleEdges[i] != -1; i += 3) {
            int edge1 = triangleEdges[i];
            int edge2 = triangleEdges[i+1];
            int edge3 = triangleEdges[i+2];

            if (edgeVertices[edge1] != null && edgeVertices[edge2] != null && edgeVertices[edge3] != null) {
                int baseIndex = data.positions.size() / 3;

                data.positions.add(edgeVertices[edge1][0]);
                data.positions.add(edgeVertices[edge1][1]);
                data.positions.add(edgeVertices[edge1][2]);

                data.positions.add(edgeVertices[edge2][0]);
                data.positions.add(edgeVertices[edge2][1]);
                data.positions.add(edgeVertices[edge2][2]);

                data.positions.add(edgeVertices[edge3][0]);
                data.positions.add(edgeVertices[edge3][1]);
                data.positions.add(edgeVertices[edge3][2]);

                calculateAndAddNormal(data, baseIndex);

                data.indices.add(baseIndex);
                data.indices.add(baseIndex + 1);
                data.indices.add(baseIndex + 2);
            }
        }
    }

    private static float[] interpolateVertex(int x1, int y1, int z1, int x2, int y2, int z2,
                                             float d1, float d2, float threshold) {
        if (Math.abs(d1 - d2) < 0.00001f) {
            return new float[]{(x1 + x2) / 2.0f, (y1 + y2) / 2.0f, (z1 + z2) / 2.0f};
        }

        if (Math.abs(threshold - d1) < 0.00001f) {
            return new float[]{x1, y1, z1};
        }

        if (Math.abs(threshold - d2) < 0.00001f) {
            return new float[]{x2, y2, z2};
        }

        float t = (threshold - d1) / (d2 - d1);
        t = Math.max(0.0f, Math.min(1.0f, t));

        return new float[]{
                x1 + t * (x2 - x1),
                y1 + t * (y2 - y1),
                z1 + t * (z2 - z1)
        };
    }

    private static void calculateAndAddNormal(MeshData data, int baseIndex) {
        int idx = baseIndex * 3;
        float[] v0 = {
                data.positions.get(idx),
                data.positions.get(idx + 1),
                data.positions.get(idx + 2)
        };
        float[] v1 = {
                data.positions.get(idx + 3),
                data.positions.get(idx + 4),
                data.positions.get(idx + 5)
        };

        float[] v2 = {
                data.positions.get(idx + 6),
                data.positions.get(idx + 7),
                data.positions.get(idx + 8)
        };

        float[] edge1 = {
                v1[0] - v0[0],
                v1[1] - v0[1],
                v1[2] - v0[2]
        };

        float[] edge2 = {
                v2[0] - v0[0],
                v2[1] - v0[1],
                v2[2] - v0[2]
        };

        float[] normal = {
                edge1[1] * edge2[2] - edge1[2] * edge2[1],
                edge1[2] * edge2[0] - edge1[0] * edge2[2],
                edge1[0] * edge2[1] - edge1[1] * edge2[0]
        };

        float length = (float)Math.sqrt(
                normal[0] * normal[0] +
                        normal[1] * normal[1] +
                        normal[2] * normal[2]
        );

        if (length > 0.00001f) {
            normal[0] /= length;
            normal[1] /= length;
            normal[2] /= length;
        }

        for (int i = 0; i < 3; i++) {
            data.normals.add(normal[0]);
            data.normals.add(normal[1]);
            data.normals.add(normal[2]);
        }
    }

    private static void smoothDensityField(float[][][] density) {
        float[][][] temp = new float[18][18][18];

        for (int x = 0; x < 18; x++) {
            for (int y = 0; y < 18; y++) {
                for (int z = 0; z < 18; z++) {
                    temp[x][y][z] = density[x][y][z];
                }
            }
        }

        for (int x = 0; x < 18; x++) {
            for (int y = 0; y < 18; y++) {
                for (int z = 0; z < 18; z++) {
                    float sum = 0;
                    int count = 0;

                    // Use 3x3x3 kernel with bounds checking
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                int nx = x + dx;
                                int ny = y + dy;
                                int nz = z + dz;

                                if (nx >= 0 && nx < 18 && ny >= 0 && ny < 18 && nz >= 0 && nz < 18) {
                                    sum += density[nx][ny][nz];
                                    count++;
                                }
                            }
                        }
                    }

                    if (count > 0) {
                        temp[x][y][z] = sum / count;
                    }
                }
            }
        }

        for (int x = 0; x < 18; x++) {
            for (int y = 0; y < 18; y++) {
                for (int z = 0; z < 18; z++) {
                    density[x][y][z] = temp[x][y][z];
                }
            }
        }
    }

    // For any edge, if one vertex is inside of the surface and the other is
    // outside of the surface
    // then the edge intersects the surface
    // For each of the 8 vertices of the cube can be two possible states :
    // either inside or outside of the surface
    // For any cube the are 2^8=256 possible sets of vertex states
    // This table lists the edges intersected by the surface for all 256
    // possible vertex states
    // There are 12 edges. For each entry in the table, if edge #n is
    // intersected, then bit #n is set to 1
    // This table is from Paul Bourke's
    // (http://paulbourke.net/geometry/polygonise/)
    // Marching Cubes implementation.
    private static final int[] EDGE_TABLE = new int[] { 0x000, 0x109, 0x203,
            0x30a, 0x406, 0x50f, 0x605, 0x70c, 0x80c, 0x905, 0xa0f, 0xb06, 0xc0a, 0xd03,
            0xe09, 0xf00, 0x190, 0x099, 0x393, 0x29a, 0x596, 0x49f, 0x795, 0x69c, 0x99c,
            0x895, 0xb9f, 0xa96, 0xd9a, 0xc93, 0xf99, 0xe90, 0x230, 0x339, 0x033, 0x13a,
            0x636, 0x73f, 0x435, 0x53c, 0xa3c, 0xb35, 0x83f, 0x936, 0xe3a, 0xf33, 0xc39,
            0xd30, 0x3a0, 0x2a9, 0x1a3, 0x0aa, 0x7a6, 0x6af, 0x5a5, 0x4ac, 0xbac, 0xaa5,
            0x9af, 0x8a6, 0xfaa, 0xea3, 0xda9, 0xca0, 0x460, 0x569, 0x663, 0x76a, 0x066,
            0x16f, 0x265, 0x36c, 0xc6c, 0xd65, 0xe6f, 0xf66, 0x86a, 0x963, 0xa69, 0xb60,
            0x5f0, 0x4f9, 0x7f3, 0x6fa, 0x1f6, 0x0ff, 0x3f5, 0x2fc, 0xdfc, 0xcf5, 0xfff,
            0xef6, 0x9fa, 0x8f3, 0xbf9, 0xaf0, 0x650, 0x759, 0x453, 0x55a, 0x256, 0x35f,
            0x055, 0x15c, 0xe5c, 0xf55, 0xc5f, 0xd56, 0xa5a, 0xb53, 0x859, 0x950, 0x7c0,
            0x6c9, 0x5c3, 0x4ca, 0x3c6, 0x2cf, 0x1c5, 0x0cc, 0xfcc, 0xec5, 0xdcf, 0xcc6,
            0xbca, 0xac3, 0x9c9, 0x8c0, 0x8c0, 0x9c9, 0xac3, 0xbca, 0xcc6, 0xdcf, 0xec5,
            0xfcc, 0x0cc, 0x1c5, 0x2cf, 0x3c6, 0x4ca, 0x5c3, 0x6c9, 0x7c0, 0x950, 0x859,
            0xb53, 0xa5a, 0xd56, 0xc5f, 0xf55, 0xe5c, 0x15c, 0x055, 0x35f, 0x256, 0x55a,
            0x453, 0x759, 0x650, 0xaf0, 0xbf9, 0x8f3, 0x9fa, 0xef6, 0xfff, 0xcf5, 0xdfc,
            0x2fc, 0x3f5, 0x0ff, 0x1f6, 0x6fa, 0x7f3, 0x4f9, 0x5f0, 0xb60, 0xa69, 0x963,
            0x86a, 0xf66, 0xe6f, 0xd65, 0xc6c, 0x36c, 0x265, 0x16f, 0x066, 0x76a, 0x663,
            0x569, 0x460, 0xca0, 0xda9, 0xea3, 0xfaa, 0x8a6, 0x9af, 0xaa5, 0xbac, 0x4ac,
            0x5a5, 0x6af, 0x7a6, 0x0aa, 0x1a3, 0x2a9, 0x3a0, 0xd30, 0xc39, 0xf33, 0xe3a,
            0x936, 0x83f, 0xb35, 0xa3c, 0x53c, 0x435, 0x73f, 0x636, 0x13a, 0x033, 0x339,
            0x230, 0xe90, 0xf99, 0xc93, 0xd9a, 0xa96, 0xb9f, 0x895, 0x99c, 0x69c, 0x795,
            0x49f, 0x596, 0x29a, 0x393, 0x099, 0x190, 0xf00, 0xe09, 0xd03, 0xc0a, 0xb06,
            0xa0f, 0x905, 0x80c, 0x70c, 0x605, 0x50f, 0x406, 0x30a, 0x203, 0x109,
            0x000 };

    // For each of the possible cube state there is a specific triangulation
    // of the edge intersection points. This table lists all of
    // them in the form of
    // 0-5 edge triples with the list terminated by the invalid value -1.
    // For example: TRIANGLE_TABLE[3] list the 2 triangles formed
    // when corner[0]
    // and corner[1] are inside of the surface, but the rest of the cube is not.
    //
    // This table is from Paul Bourke's
    // (http://paulbourke.net/geometry/polygonise/)
    // Marching Cubes implementation.
    private static final int[][] TRIANGLE_TABLE = new int[][] { { -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 0, 8, 3, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 0, 1, 9, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1 }, { 1, 8, 3, 9, 8, 1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1 }, { 1, 2, 10, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, { 0, 8, 3, 1, 2, 10, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1 }, { 9, 2, 10, 0, 2, 9, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 2, 8, 3, 2, 10, 8, 10, 9, 8, -1, -1, -1, -1, -1, -1,
            -1 }, { 3, 11, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 0, 11, 2, 8, 11, 0, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1 }, { 1, 9, 0, 2, 3, 11, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, { 1, 11, 2, 1, 9, 11, 9, 8, 11, -1,
            -1, -1, -1, -1, -1, -1 }, { 3, 10, 1, 11, 10, 3, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 0, 10, 1, 0,
            8, 10, 8, 11, 10, -1, -1, -1, -1, -1, -1, -1 }, {
            3, 9, 0, 3, 11, 9, 11, 10, 9, -1, -1, -1, -1,
            -1, -1, -1 }, { 9, 8, 10, 10, 8, 11, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1 }, { 4, 7, 8, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 4, 3, 0, 7, 3, 4, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1 }, { 0, 1, 9, 8, 4,
            7, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 4, 1, 9, 4, 7, 1, 7, 3, 1, -1,
            -1, -1, -1, -1, -1, -1 }, { 1, 2, 10,
            8, 4, 7, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1 }, { 3, 4, 7, 3, 0, 4, 1,
            2, 10, -1, -1, -1, -1, -1, -1,
            -1 }, { 9, 2, 10, 9, 0, 2, 8, 4,
            7, -1, -1, -1, -1, -1, -1, -1 },
            { 2, 10, 9, 2, 9, 7, 2, 7, 3, 7, 9, 4, -1, -1, -1, -1 }, { 8, 4, 7, 3, 11,
            2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 11, 4, 7, 11, 2, 4, 2, 0,
            4, -1, -1, -1, -1, -1, -1, -1 }, { 9, 0, 1, 8, 4, 7, 2, 3, 11, -1, -1,
            -1, -1, -1, -1, -1 }, { 4, 7, 11, 9, 4, 11, 9, 11, 2, 9, 2, 1, -1, -1,
            -1, -1 }, { 3, 10, 1, 3, 11, 10, 7, 8, 4, -1, -1, -1, -1, -1, -1,
            -1 }, { 1, 11, 10, 1, 4, 11, 1, 0, 4, 7, 11, 4, -1, -1, -1, -1 },
            { 4, 7, 8, 9, 0, 11, 9, 11, 10, 11, 0, 3, -1, -1, -1, -1 }, { 4, 7, 11, 4,
            11, 9, 9, 11, 10, -1, -1, -1, -1, -1, -1, -1 }, { 9, 5, 4, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 9, 5, 4, 0, 8, 3, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1 }, { 0, 5, 4, 1, 5, 0, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1 }, { 8, 5, 4, 8, 3, 5, 3, 1, 5, -1, -1, -1, -1,
            -1, -1, -1 }, { 1, 2, 10, 9, 5, 4, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 3, 0, 8, 1, 2, 10, 4, 9, 5, -1, -1, -1, -1, -1, -1,
            -1 }, { 5, 2, 10, 5, 4, 2, 4, 0, 2, -1, -1, -1, -1, -1, -1,
            -1 }, { 2, 10, 5, 3, 2, 5, 3, 5, 4, 3, 4, 8, -1, -1, -1,
            -1 }, { 9, 5, 4, 2, 3, 11, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 0, 11, 2, 0, 8, 11, 4, 9, 5, -1, -1, -1, -1,
            -1, -1, -1 }, { 0, 5, 4, 0, 1, 5, 2, 3, 11, -1, -1,
            -1, -1, -1, -1, -1 }, { 2, 1, 5, 2, 5, 8, 2, 8, 11,
            4, 8, 5, -1, -1, -1, -1 }, { 10, 3, 11, 10, 1, 3,
            9, 5, 4, -1, -1, -1, -1, -1, -1, -1 }, { 4, 9,
            5, 0, 8, 1, 8, 10, 1, 8, 11, 10, -1, -1, -1,
            -1 }, { 5, 4, 0, 5, 0, 11, 5, 11, 10, 11, 0,
            3, -1, -1, -1, -1 }, { 5, 4, 8, 5, 8, 10,
            10, 8, 11, -1, -1, -1, -1, -1, -1, -1 }, {
            9, 7, 8, 5, 7, 9, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1 }, { 9, 3, 0, 9, 5,
            3, 5, 7, 3, -1, -1, -1, -1, -1, -1,
            -1 }, { 0, 7, 8, 0, 1, 7, 1, 5, 7, -1,
            -1, -1, -1, -1, -1, -1 }, { 1, 5, 3,
            3, 5, 7, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, { 9, 7, 8, 9, 5,
            7, 10, 1, 2, -1, -1, -1, -1, -1,
            -1, -1 }, { 10, 1, 2, 9, 5, 0,
            5, 3, 0, 5, 7, 3, -1, -1, -1,
            -1 }, { 8, 0, 2, 8, 2, 5, 8,
            5, 7, 10, 5, 2, -1, -1, -1,
            -1 }, { 2, 10, 5, 2, 5, 3,
            3, 5, 7, -1, -1, -1, -1,
            -1, -1, -1 }, { 7, 9, 5,
            7, 8, 9, 3, 11, 2, -1,
            -1, -1, -1, -1, -1,
            -1 }, { 9, 5, 7, 9, 7,
            2, 9, 2, 0, 2, 7, 11,
            -1, -1, -1, -1 }, { 2,
            3, 11, 0, 1, 8, 1,
            7, 8, 1, 5, 7, -1,
            -1, -1, -1 }, { 11,
            2, 1, 11, 1, 7, 7,
            1, 5, -1, -1, -1,
            -1, -1, -1, -1 },
            { 9, 5, 8, 8, 5, 7, 10, 1, 3, 10, 3, 11, -1, -1, -1, -1 }, { 5, 7, 0, 5, 0,
            9, 7, 11, 0, 1, 0, 10, 11, 10, 0, -1 }, { 11, 10, 0, 11, 0, 3, 10, 5, 0,
            8, 0, 7, 5, 7, 0, -1 }, { 11, 10, 5, 7, 11, 5, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, { 10, 6, 5, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1 }, { 0, 8, 3, 5, 10, 6, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 9, 0, 1, 5, 10, 6, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 1, 8, 3, 1, 9, 8, 5, 10, 6, -1, -1, -1, -1, -1, -1,
            -1 }, { 1, 6, 5, 2, 6, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 1, 6, 5, 1, 2, 6, 3, 0, 8, -1, -1, -1, -1, -1, -1,
            -1 }, { 9, 6, 5, 9, 0, 6, 0, 2, 6, -1, -1, -1, -1, -1, -1,
            -1 }, { 5, 9, 8, 5, 8, 2, 5, 2, 6, 3, 2, 8, -1, -1, -1,
            -1 }, { 2, 3, 11, 10, 6, 5, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, { 11, 0, 8, 11, 2, 0, 10, 6, 5,
            -1, -1, -1, -1, -1, -1, -1 }, { 0, 1, 9, 2, 3, 11,
            5, 10, 6, -1, -1, -1, -1, -1, -1, -1 }, { 5, 10,
            6, 1, 9, 2, 9, 11, 2, 9, 8, 11, -1, -1, -1,
            -1 }, { 6, 3, 11, 6, 5, 3, 5, 1, 3, -1, -1,
            -1, -1, -1, -1, -1 }, { 0, 8, 11, 0, 11, 5,
            0, 5, 1, 5, 11, 6, -1, -1, -1, -1 }, { 3,
            11, 6, 0, 3, 6, 0, 6, 5, 0, 5, 9, -1,
            -1, -1, -1 }, { 6, 5, 9, 6, 9, 11, 11,
            9, 8, -1, -1, -1, -1, -1, -1, -1 }, {
            5, 10, 6, 4, 7, 8, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1 }, { 4, 3, 0,
            4, 7, 3, 6, 5, 10, -1, -1, -1, -1,
            -1, -1, -1 }, { 1, 9, 0, 5, 10, 6,
            8, 4, 7, -1, -1, -1, -1, -1, -1,
            -1 }, { 10, 6, 5, 1, 9, 7, 1, 7,
            3, 7, 9, 4, -1, -1, -1, -1 },
            { 6, 1, 2, 6, 5, 1, 4, 7, 8, -1, -1, -1, -1, -1, -1, -1 }, { 1, 2, 5, 5, 2,
            6, 3, 0, 4, 3, 4, 7, -1, -1, -1, -1 }, { 8, 4, 7, 9, 0, 5, 0, 6, 5, 0, 2,
            6, -1, -1, -1, -1 }, { 7, 3, 9, 7, 9, 4, 3, 2, 9, 5, 9, 6, 2, 6, 9,
            -1 }, { 3, 11, 2, 7, 8, 4, 10, 6, 5, -1, -1, -1, -1, -1, -1, -1 }, {
            5, 10, 6, 4, 7, 2, 4, 2, 0, 2, 7, 11, -1, -1, -1, -1 }, { 0, 1, 9,
            4, 7, 8, 2, 3, 11, 5, 10, 6, -1, -1, -1, -1 }, { 9, 2, 1, 9, 11,
            2, 9, 4, 11, 7, 11, 4, 5, 10, 6, -1 }, { 8, 4, 7, 3, 11, 5, 3,
            5, 1, 5, 11, 6, -1, -1, -1, -1 }, { 5, 1, 11, 5, 11, 6, 1, 0,
            11, 7, 11, 4, 0, 4, 11, -1 }, { 0, 5, 9, 0, 6, 5, 0, 3, 6,
            11, 6, 3, 8, 4, 7, -1 }, { 6, 5, 9, 6, 9, 11, 4, 7, 9, 7,
            11, 9, -1, -1, -1, -1 }, { 10, 4, 9, 6, 4, 10, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1 }, { 4, 10, 6, 4, 9,
            10, 0, 8, 3, -1, -1, -1, -1, -1, -1, -1 }, { 10, 0,
            1, 10, 6, 0, 6, 4, 0, -1, -1, -1, -1, -1, -1,
            -1 }, { 8, 3, 1, 8, 1, 6, 8, 6, 4, 6, 1, 10, -1,
            -1, -1, -1 }, { 1, 4, 9, 1, 2, 4, 2, 6, 4, -1,
            -1, -1, -1, -1, -1, -1 }, { 3, 0, 8, 1, 2, 9,
            2, 4, 9, 2, 6, 4, -1, -1, -1, -1 }, { 0, 2,
            4, 4, 2, 6, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1 }, { 8, 3, 2, 8, 2, 4, 4, 2, 6,
            -1, -1, -1, -1, -1, -1, -1 }, { 10, 4,
            9, 10, 6, 4, 11, 2, 3, -1, -1, -1, -1,
            -1, -1, -1 }, { 0, 8, 2, 2, 8, 11, 4,
            9, 10, 4, 10, 6, -1, -1, -1, -1 }, {
            3, 11, 2, 0, 1, 6, 0, 6, 4, 6, 1,
            10, -1, -1, -1, -1 }, { 6, 4, 1,
            6, 1, 10, 4, 8, 1, 2, 1, 11, 8,
            11, 1, -1 }, { 9, 6, 4, 9, 3, 6,
            9, 1, 3, 11, 6, 3, -1, -1, -1,
            -1 }, { 8, 11, 1, 8, 1, 0, 11,
            6, 1, 9, 1, 4, 6, 4, 1,
            -1 }, { 3, 11, 6, 3, 6, 0,
            0, 6, 4, -1, -1, -1, -1,
            -1, -1, -1 }, { 6, 4, 8,
            11, 6, 8, -1, -1, -1,
            -1, -1, -1, -1, -1, -1,
            -1 }, { 7, 10, 6, 7, 8,
            10, 8, 9, 10, -1, -1,
            -1, -1, -1, -1, -1 },
            { 0, 7, 3, 0, 10, 7, 0, 9, 10, 6, 7, 10, -1, -1, -1, -1 }, { 10, 6, 7, 1,
            10, 7, 1, 7, 8, 1, 8, 0, -1, -1, -1, -1 }, { 10, 6, 7, 10, 7, 1, 1, 7, 3,
            -1, -1, -1, -1, -1, -1, -1 }, { 1, 2, 6, 1, 6, 8, 1, 8, 9, 8, 6, 7, -1,
            -1, -1, -1 }, { 2, 6, 9, 2, 9, 1, 6, 7, 9, 0, 9, 3, 7, 3, 9, -1 }, {
            7, 8, 0, 7, 0, 6, 6, 0, 2, -1, -1, -1, -1, -1, -1, -1 }, { 7, 3, 2,
            6, 7, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 2, 3, 11, 10,
            6, 8, 10, 8, 9, 8, 6, 7, -1, -1, -1, -1 }, { 2, 0, 7, 2, 7, 11,
            0, 9, 7, 6, 7, 10, 9, 10, 7, -1 }, { 1, 8, 0, 1, 7, 8, 1, 10,
            7, 6, 7, 10, 2, 3, 11, -1 }, { 11, 2, 1, 11, 1, 7, 10, 6, 1,
            6, 7, 1, -1, -1, -1, -1 }, { 8, 9, 6, 8, 6, 7, 9, 1, 6,
            11, 6, 3, 1, 3, 6, -1 }, { 0, 9, 1, 11, 6, 7, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1 }, { 7, 8, 0, 7, 0, 6,
            3, 11, 0, 11, 6, 0, -1, -1, -1, -1 }, { 7, 11, 6,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 7, 6, 11, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1 }, { 3, 0, 8, 11, 7, 6, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 0, 1,
            9, 11, 7, 6, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 8, 1, 9, 8, 3, 1, 11, 7, 6, -1,
            -1, -1, -1, -1, -1, -1 }, { 10, 1, 2, 6,
            11, 7, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 1, 2, 10, 3, 0, 8, 6, 11, 7,
            -1, -1, -1, -1, -1, -1, -1 }, { 2, 9,
            0, 2, 10, 9, 6, 11, 7, -1, -1, -1,
            -1, -1, -1, -1 }, { 6, 11, 7, 2, 10,
            3, 10, 8, 3, 10, 9, 8, -1, -1, -1,
            -1 }, { 7, 2, 3, 6, 2, 7, -1, -1,
            -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 7, 0, 8, 7, 6, 0, 6, 2,
            0, -1, -1, -1, -1, -1, -1,
            -1 }, { 2, 7, 6, 2, 3, 7, 0,
            1, 9, -1, -1, -1, -1, -1,
            -1, -1 }, { 1, 6, 2, 1, 8,
            6, 1, 9, 8, 8, 7, 6, -1,
            -1, -1, -1 }, { 10, 7, 6,
            10, 1, 7, 1, 3, 7, -1,
            -1, -1, -1, -1, -1,
            -1 }, { 10, 7, 6, 1, 7,
            10, 1, 8, 7, 1, 0, 8,
            -1, -1, -1, -1 }, { 0,
            3, 7, 0, 7, 10, 0,
            10, 9, 6, 10, 7, -1,
            -1, -1, -1 }, { 7,
            6, 10, 7, 10, 8,
            8, 10, 9, -1, -1,
            -1, -1, -1, -1,
            -1 }, { 6, 8, 4,
            11, 8, 6, -1,
            -1, -1, -1, -1,
            -1, -1, -1, -1,
            -1 }, { 3, 6,
            11, 3, 0, 6,
            0, 4, 6, -1,
            -1, -1, -1,
            -1, -1, -1 },
            { 8, 6, 11, 8, 4, 6, 9, 0, 1, -1, -1, -1, -1, -1, -1, -1 }, { 9, 4, 6, 9, 6,
            3, 9, 3, 1, 11, 3, 6, -1, -1, -1, -1 }, { 6, 8, 4, 6, 11, 8, 2, 10, 1, -1,
            -1, -1, -1, -1, -1, -1 }, { 1, 2, 10, 3, 0, 11, 0, 6, 11, 0, 4, 6, -1,
            -1, -1, -1 }, { 4, 11, 8, 4, 6, 11, 0, 2, 9, 2, 10, 9, -1, -1, -1,
            -1 }, { 10, 9, 3, 10, 3, 2, 9, 4, 3, 11, 3, 6, 4, 6, 3, -1 }, { 8,
            2, 3, 8, 4, 2, 4, 6, 2, -1, -1, -1, -1, -1, -1, -1 }, { 0, 4, 2,
            4, 6, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 1, 9, 0, 2,
            3, 4, 2, 4, 6, 4, 3, 8, -1, -1, -1, -1 }, { 1, 9, 4, 1, 4, 2,
            2, 4, 6, -1, -1, -1, -1, -1, -1, -1 }, { 8, 1, 3, 8, 6, 1,
            8, 4, 6, 6, 10, 1, -1, -1, -1, -1 }, { 10, 1, 0, 10, 0, 6,
            6, 0, 4, -1, -1, -1, -1, -1, -1, -1 }, { 4, 6, 3, 4, 3,
            8, 6, 10, 3, 0, 3, 9, 10, 9, 3, -1 }, { 10, 9, 4, 6,
            10, 4, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, {
            4, 9, 5, 7, 6, 11, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 0, 8, 3, 4, 9, 5, 11, 7, 6, -1, -1,
            -1, -1, -1, -1, -1 }, { 5, 0, 1, 5, 4, 0, 7, 6,
            11, -1, -1, -1, -1, -1, -1, -1 }, { 11, 7, 6,
            8, 3, 4, 3, 5, 4, 3, 1, 5, -1, -1, -1, -1 },
            { 9, 5, 4, 10, 1, 2, 7, 6, 11, -1, -1, -1, -1, -1, -1, -1 }, { 6, 11, 7, 1,
            2, 10, 0, 8, 3, 4, 9, 5, -1, -1, -1, -1 }, { 7, 6, 11, 5, 4, 10, 4, 2, 10,
            4, 0, 2, -1, -1, -1, -1 }, { 3, 4, 8, 3, 5, 4, 3, 2, 5, 10, 5, 2, 11, 7,
            6, -1 }, { 7, 2, 3, 7, 6, 2, 5, 4, 9, -1, -1, -1, -1, -1, -1, -1 }, {
            9, 5, 4, 0, 8, 6, 0, 6, 2, 6, 8, 7, -1, -1, -1, -1 }, { 3, 6, 2, 3,
            7, 6, 1, 5, 0, 5, 4, 0, -1, -1, -1, -1 }, { 6, 2, 8, 6, 8, 7, 2,
            1, 8, 4, 8, 5, 1, 5, 8, -1 }, { 9, 5, 4, 10, 1, 6, 1, 7, 6, 1,
            3, 7, -1, -1, -1, -1 }, { 1, 6, 10, 1, 7, 6, 1, 0, 7, 8, 7, 0,
            9, 5, 4, -1 }, { 4, 0, 10, 4, 10, 5, 0, 3, 10, 6, 10, 7, 3,
            7, 10, -1 }, { 7, 6, 10, 7, 10, 8, 5, 4, 10, 4, 8, 10, -1,
            -1, -1, -1 }, { 6, 9, 5, 6, 11, 9, 11, 8, 9, -1, -1, -1,
            -1, -1, -1, -1 }, { 3, 6, 11, 0, 6, 3, 0, 5, 6, 0, 9,
            5, -1, -1, -1, -1 }, { 0, 11, 8, 0, 5, 11, 0, 1, 5,
            5, 6, 11, -1, -1, -1, -1 }, { 6, 11, 3, 6, 3, 5,
            5, 3, 1, -1, -1, -1, -1, -1, -1, -1 }, { 1, 2,
            10, 9, 5, 11, 9, 11, 8, 11, 5, 6, -1, -1, -1,
            -1 }, { 0, 11, 3, 0, 6, 11, 0, 9, 6, 5, 6, 9,
            1, 2, 10, -1 }, { 11, 8, 5, 11, 5, 6, 8, 0,
            5, 10, 5, 2, 0, 2, 5, -1 }, { 6, 11, 3, 6,
            3, 5, 2, 10, 3, 10, 5, 3, -1, -1, -1,
            -1 }, { 5, 8, 9, 5, 2, 8, 5, 6, 2, 3, 8,
            2, -1, -1, -1, -1 }, { 9, 5, 6, 9, 6,
            0, 0, 6, 2, -1, -1, -1, -1, -1, -1,
            -1 }, { 1, 5, 8, 1, 8, 0, 5, 6, 8,
            3, 8, 2, 6, 2, 8, -1 }, { 1, 5, 6,
            2, 1, 6, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, { 1, 3, 6, 1,
            6, 10, 3, 8, 6, 5, 6, 9, 8, 9,
            6, -1 }, { 10, 1, 0, 10, 0, 6,
            9, 5, 0, 5, 6, 0, -1, -1,
            -1, -1 }, { 0, 3, 8, 5, 6,
            10, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1 }, {
            10, 5, 6, -1, -1, -1,
            -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, { 11,
            5, 10, 7, 5, 11, -1,
            -1, -1, -1, -1, -1,
            -1, -1, -1, -1 }, {
            11, 5, 10, 11, 7, 5,
            8, 3, 0, -1, -1, -1,
            -1, -1, -1, -1 }, {
            5, 11, 7, 5, 10,
            11, 1, 9, 0, -1,
            -1, -1, -1, -1,
            -1, -1 }, { 10, 7,
            5, 10, 11, 7, 9,
            8, 1, 8, 3, 1,
            -1, -1, -1,
            -1 }, { 11, 1,
            2, 11, 7, 1,
            7, 5, 1, -1,
            -1, -1, -1,
            -1, -1, -1 },
            { 0, 8, 3, 1, 2, 7, 1, 7, 5, 7, 2, 11, -1, -1, -1, -1 }, { 9, 7, 5, 9, 2, 7,
            9, 0, 2, 2, 11, 7, -1, -1, -1, -1 }, { 7, 5, 2, 7, 2, 11, 5, 9, 2, 3, 2,
            8, 9, 8, 2, -1 }, { 2, 5, 10, 2, 3, 5, 3, 7, 5, -1, -1, -1, -1, -1, -1,
            -1 }, { 8, 2, 0, 8, 5, 2, 8, 7, 5, 10, 2, 5, -1, -1, -1, -1 }, { 9, 0,
            1, 5, 10, 3, 5, 3, 7, 3, 10, 2, -1, -1, -1, -1 }, { 9, 8, 2, 9, 2,
            1, 8, 7, 2, 10, 2, 5, 7, 5, 2, -1 }, { 1, 3, 5, 3, 7, 5, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1 }, { 0, 8, 7, 0, 7, 1, 1, 7, 5,
            -1, -1, -1, -1, -1, -1, -1 }, { 9, 0, 3, 9, 3, 5, 5, 3, 7, -1,
            -1, -1, -1, -1, -1, -1 }, { 9, 8, 7, 5, 9, 7, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1 }, { 5, 8, 4, 5, 10, 8, 10, 11,
            8, -1, -1, -1, -1, -1, -1, -1 }, { 5, 0, 4, 5, 11, 0, 5,
            10, 11, 11, 3, 0, -1, -1, -1, -1 }, { 0, 1, 9, 8, 4,
            10, 8, 10, 11, 10, 4, 5, -1, -1, -1, -1 }, { 10, 11,
            4, 10, 4, 5, 11, 3, 4, 9, 4, 1, 3, 1, 4, -1 }, {
            2, 5, 1, 2, 8, 5, 2, 11, 8, 4, 5, 8, -1, -1, -1,
            -1 }, { 0, 4, 11, 0, 11, 3, 4, 5, 11, 2, 11, 1,
            5, 1, 11, -1 }, { 0, 2, 5, 0, 5, 9, 2, 11, 5,
            4, 5, 8, 11, 8, 5, -1 }, { 9, 4, 5, 2, 11,
            3, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 2, 5, 10, 3, 5, 2, 3, 4, 5, 3, 8,
            4, -1, -1, -1, -1 }, { 5, 10, 2, 5, 2,
            4, 4, 2, 0, -1, -1, -1, -1, -1, -1,
            -1 }, { 3, 10, 2, 3, 5, 10, 3, 8, 5,
            4, 5, 8, 0, 1, 9, -1 }, { 5, 10, 2,
            5, 2, 4, 1, 9, 2, 9, 4, 2, -1, -1,
            -1, -1 }, { 8, 4, 5, 8, 5, 3, 3,
            5, 1, -1, -1, -1, -1, -1, -1,
            -1 }, { 0, 4, 5, 1, 0, 5, -1,
            -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 8, 4, 5, 8, 5, 3,
            9, 0, 5, 0, 3, 5, -1, -1,
            -1, -1 }, { 9, 4, 5, -1, -1,
            -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1 }, { 4,
            11, 7, 4, 9, 11, 9, 10,
            11, -1, -1, -1, -1, -1,
            -1, -1 }, { 0, 8, 3, 4,
            9, 7, 9, 11, 7, 9, 10,
            11, -1, -1, -1, -1 },
            { 1, 10, 11, 1, 11, 4, 1, 4, 0, 7, 4, 11, -1, -1, -1, -1 }, { 3, 1, 4, 3, 4,
            8, 1, 10, 4, 7, 4, 11, 10, 11, 4, -1 }, { 4, 11, 7, 9, 11, 4, 9, 2, 11, 9,
            1, 2, -1, -1, -1, -1 }, { 9, 7, 4, 9, 11, 7, 9, 1, 11, 2, 11, 1, 0, 8,
            3, -1 }, { 11, 7, 4, 11, 4, 2, 2, 4, 0, -1, -1, -1, -1, -1, -1, -1 },
            { 11, 7, 4, 11, 4, 2, 8, 3, 4, 3, 2, 4, -1, -1, -1, -1 }, { 2, 9, 10, 2, 7,
            9, 2, 3, 7, 7, 4, 9, -1, -1, -1, -1 }, { 9, 10, 7, 9, 7, 4, 10, 2, 7, 8,
            7, 0, 2, 0, 7, -1 }, { 3, 7, 10, 3, 10, 2, 7, 4, 10, 1, 10, 0, 4, 0, 10,
            -1 }, { 1, 10, 2, 8, 7, 4, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, {
            4, 9, 1, 4, 1, 7, 7, 1, 3, -1, -1, -1, -1, -1, -1, -1 }, { 4, 9, 1,
            4, 1, 7, 0, 8, 1, 8, 7, 1, -1, -1, -1, -1 }, { 4, 0, 3, 7, 4, 3,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 4, 8, 7, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 9, 10, 8, 10, 11,
            8, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 3, 0, 9, 3,
            9, 11, 11, 9, 10, -1, -1, -1, -1, -1, -1, -1 }, { 0, 1,
            10, 0, 10, 8, 8, 10, 11, -1, -1, -1, -1, -1, -1, -1 }, {
            3, 1, 10, 11, 3, 10, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 1, 2, 11, 1, 11, 9, 9, 11, 8, -1, -1, -1,
            -1, -1, -1, -1 }, { 3, 0, 9, 3, 9, 11, 1, 2, 9, 2,
            11, 9, -1, -1, -1, -1 }, { 0, 2, 11, 8, 0, 11, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1 }, { 3, 2,
            11, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1 }, { 2, 3, 8, 2, 8, 10, 10, 8, 9,
            -1, -1, -1, -1, -1, -1, -1 }, { 9, 10, 2, 0,
            9, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1 }, { 2, 3, 8, 2, 8, 10, 0, 1, 8, 1, 10,
            8, -1, -1, -1, -1 }, { 1, 10, 2, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1 }, { 1, 3, 8, 9, 1, 8, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1 }, {
            0, 9, 1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1 }, { 0,
            3, 8, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1 }, {
            -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1,
            -1, -1 } };
}
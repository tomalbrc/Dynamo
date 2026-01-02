package de.tomalbrc.dynamo.impl.physics;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.math.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class VoxelChunk {
    private static final int SIZE = 16;
    private final boolean[][][] voxels;

    public VoxelChunk(boolean[][][] voxels) {
        this.voxels = voxels;
    }

    private static int getIndex(int x, int y, int z) {
        return x | (y << 4) | (z << 8);
    }

    public void generateCollisionShape(CompoundCollisionShape shape) {
        boolean[][][] tested = new boolean[SIZE][SIZE][SIZE];
        Map<int[], int[]> boxes = new HashMap<>();

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (!tested[x][y][z] && voxels[x][y][z]) {
                        tested[x][y][z] = true;

                        int[] start = new int[]{x, y, z};
                        int[] size = new int[]{1, 1, 1};

                        boolean canSpreadX = true;
                        boolean canSpreadY = true;
                        boolean canSpreadZ = true;

                        while (canSpreadX || canSpreadY || canSpreadZ) {
                            canSpreadX = trySpreadX(canSpreadX, tested, start, size);
                            canSpreadY = trySpreadY(canSpreadY, tested, start, size);
                            canSpreadZ = trySpreadZ(canSpreadZ, tested, start, size);
                        }

                        boxes.put(start, size);
                    }
                }
            }
        }

        buildCompoundShape(shape, boxes);
    }

    private boolean trySpreadX(boolean canSpreadX, boolean[][][] tested, int[] start, int[] size) {
        int yLimit = start[1] + size[1];
        int zLimit = start[2] + size[2];

        for (int y = start[1]; y < yLimit && canSpreadX; y++) {
            for (int z = start[2]; z < zLimit; z++) {
                int newX = start[0] + size[0];
                if (newX >= SIZE || tested[newX][y][z] || !voxels[newX][y][z]) {
                    canSpreadX = false;
                }
            }
        }

        if (canSpreadX) {
            for (int y = start[1]; y < yLimit; y++) {
                for (int z = start[2]; z < zLimit; z++) {
                    tested[start[0] + size[0]][y][z] = true;
                }
            }
            size[0]++;
        }

        return canSpreadX;
    }

    private boolean trySpreadY(boolean canSpreadY, boolean[][][] tested, int[] start, int[] size) {
        int xLimit = start[0] + size[0];
        int zLimit = start[2] + size[2];

        for (int x = start[0]; x < xLimit && canSpreadY; x++) {
            for (int z = start[2]; z < zLimit; z++) {
                int newY = start[1] + size[1];
                if (newY >= SIZE || tested[x][newY][z] || !voxels[x][newY][z]) {
                    canSpreadY = false;
                }
            }
        }

        if (canSpreadY) {
            for (int x = start[0]; x < xLimit; x++) {
                for (int z = start[2]; z < zLimit; z++) {
                    tested[x][start[1] + size[1]][z] = true;
                }
            }
            size[1]++;
        }
        return canSpreadY;
    }

    private boolean trySpreadZ(boolean canSpreadZ, boolean[][][] tested, int[] start, int[] size) {
        int xLimit = start[0] + size[0];
        int yLimit = start[1] + size[1];

        for (int x = start[0]; x < xLimit && canSpreadZ; x++) {
            for (int y = start[1]; y < yLimit; y++) {
                int newZ = start[2] + size[2];
                if (newZ >= SIZE || tested[x][y][newZ] || !voxels[x][y][newZ]) {
                    canSpreadZ = false;
                }
            }
        }

        if (canSpreadZ) {
            for (int x = start[0]; x < xLimit; x++) {
                for (int y = start[1]; y < yLimit; y++) {
                    tested[x][y][start[2] + size[2]] = true;
                }
            }
            size[2]++;
        }
        return canSpreadZ;
    }

    private void buildCompoundShape(CompoundCollisionShape shape, Map<int[], int[]> boxes) {
        for (Map.Entry<int[], int[]> entry : boxes.entrySet()) {
            int[] start = entry.getKey();
            int[] size = entry.getValue();

            // Center in Bullet coordinates
            Vector3f center = new Vector3f(
                    start[0] + size[0] / 2.0f,
                    start[1] + size[1] / 2.0f,
                    start[2] + size[2] / 2.0f
            );

            Vector3f halfExtents = new Vector3f(
                    size[0] / 2.0f,
                    size[1] / 2.0f,
                    size[2] / 2.0f
            );

            BoxCollisionShape box = new BoxCollisionShape(halfExtents);
            shape.addChildShape(box, center);
        }
    }

}

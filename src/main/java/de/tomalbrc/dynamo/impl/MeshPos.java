package de.tomalbrc.dynamo.impl;

import de.tomalbrc.dynamo.impl.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public record MeshPos(int x, int y, int z) {
    static int SIZE = ModConfig.getInstance().chunkSize;

    public static MeshPos of(BlockPos blockPos) {
        return new MeshPos(blockToMeshCoord(blockPos.getX()), blockToMeshCoord(blockPos.getY()), blockToMeshCoord(blockPos.getZ()));
    }

    public static MeshPos of(ChunkPos chunkPos) {
        return new MeshPos(blockToMeshCoord(chunkPos.getMiddleBlockX()), 0, blockToMeshCoord(chunkPos.getMiddleBlockZ()));
    }

    public static int blockToMeshCoord(int i) {
        return i/SIZE;
    }

    public static int meshToBlock(int i) {
        return i*SIZE;
    }

    public static int meshToBlock(int i, int offset) {
        return meshToBlock(i) + offset;
    }

    public static MeshPos fromLong(Long key) {
        return new MeshPos(x(key), y(key), z(key));
    }

    public static int x(long l) {
        return (int)(l << 0 >> 42);
    }

    public static int y(long l) {
        return (int)(l << 44 >> 44);
    }

    public static int z(long l) {
        return (int)(l << 22 >> 42);
    }

    public BlockPos origin() {
        return new BlockPos(meshToBlock(this.x()), meshToBlock(this.y()), meshToBlock(this.z()));
    }

    public BlockPos center() {
        return this.origin().offset(SIZE/2, SIZE/2, SIZE/2);
    }

    public int minBlockX() {
        return meshToBlock(x);
    }

    public int minBlockY() {
        return meshToBlock(y);
    }

    public int minBlockZ() {
        return meshToBlock(z);
    }

    public int maxBlockX() {
        return meshToBlock(x, SIZE-1);
    }

    public int maxBlockY() {
        return meshToBlock(y, SIZE-1);
    }

    public int maxBlockZ() {
        return meshToBlock(z, SIZE-1);
    }

    public static Set<MeshPos> inSphere(MeshPos center, double radius) {
        Set<MeshPos> positions = new HashSet<>();
        int blockRadius = (int) Math.ceil(radius);

        for (int dx = -blockRadius; dx <= blockRadius; dx++) {
            for (int dy = -blockRadius; dy <= blockRadius; dy++) {
                for (int dz = -blockRadius; dz <= blockRadius; dz++) {
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq <= radius * radius) {
                        positions.add(new MeshPos(center.x + dx, center.y + dy, center.z + dz));
                    }
                }
            }
        }

        return positions;
    }

    public static Set<MeshPos> inBox(MeshPos center, int radiusX, int radiusY, int radiusZ) {
        Set<MeshPos> positions = new HashSet<>();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    positions.add(new MeshPos(center.x + dx, center.y + dy, center.z + dz));
                }
            }
        }
        return positions;
    }

    public static Set<MeshPos> inBox(BlockPos min, BlockPos max) {
        MeshPos minMesh = MeshPos.of(min);
        MeshPos maxMesh = MeshPos.of(max);
        Set<MeshPos> positions = new HashSet<>();

        for (int ix = minMesh.x; ix <= maxMesh.x; ix++) {
            for (int iy = minMesh.y; iy <= maxMesh.y; iy++) {
                for (int iz = minMesh.z; iz <= maxMesh.z; iz++) {
                    positions.add(new MeshPos(ix, iy, iz));
                }
            }
        }
        return positions;
    }

    public long asLong() {
        return asLong(x, y, z);
    }

    public static long asLong(int i, int j, int k) {
        long l = 0L;
        l |= ((long)i & 4194303L) << 42;
        l |= ((long)j & 1048575L) << 0;
        l |= ((long)k & 4194303L) << 20;
        return l;
    }

    public String toShortString() {
        return this.x() + ", " + this.y() + ", " + this.z();
    }

    @Override
    public int hashCode() {
        return (this.y() + this.z() * 31) * 31 + this.x();
    }
}

package de.tomalbrc.dynamo.impl;

import de.tomalbrc.dynamo.impl.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.world.level.ChunkPos;

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
        return i >> 4;
    }

    public static int meshToBlock(int i) {
        return i << 4;
    }

    public static int meshToBlock(int i, int offset) {
        return meshToBlock(i) + offset;
    }

    public BlockPos origin() {
        return new BlockPos(meshToBlock(this.x()), meshToBlock(this.y()), meshToBlock(this.z()));
    }

    public BlockPos center() {
        int i = 8;
        return this.origin().offset(8, 8, 8);
    }

    public int minBlockX() {
        return meshToBlock(x, 0);
    }

    public int minBlockY() {
        return meshToBlock(y, 0);
    }

    public int minBlockZ() {
        return meshToBlock(z, 0);
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

    public static Stream<MeshPos> around(MeshPos pos, int horizontalRad, int minY, int maxY) {
        int posX = pos.x;
        int posY = pos.y;
        int posZ = pos.z;
        return betweenClosedStream(posX - horizontalRad, posY - minY, posZ - horizontalRad, posX + horizontalRad, posY + maxY, posZ + horizontalRad);
    }

    public static Stream<MeshPos> betweenClosedStream(final int i, final int j, final int k, final int l, final int m, final int n) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<>((long) (l - i + 1) * (m - j + 1) * (n - k + 1), Spliterator.SIZED) {
            final Cursor3D cursor = new Cursor3D(i, j, k, l, m, n);

            public boolean tryAdvance(Consumer<? super MeshPos> consumer) {
                if (this.cursor.advance()) {
                    consumer.accept(new MeshPos(this.cursor.nextX(), this.cursor.nextY(), this.cursor.nextZ()));
                    return true;
                } else {
                    return false;
                }
            }
        }, false);
    }

    public long asLong() {
        return asLong(x, y, z);
    }

    public static long asLong(int x, int y, int z) {
        long l = ((long)x & 4194303L) << 42;
        l |= ((long)y & 1048575L);
        l |= ((long)z & 4194303L) << 20;
        return l;
    }


    public String toShortString() {
        return this.x() + ", " + this.y() + ", " + this.z();
    }
}

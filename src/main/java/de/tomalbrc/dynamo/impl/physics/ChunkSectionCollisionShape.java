package de.tomalbrc.dynamo.impl.physics;

import de.tomalbrc.dynamo.impl.config.ModConfig;
import de.tomalbrc.dynamo.impl.mesh.ChunkMeshGenerator;
import de.tomalbrc.dynamo.impl.mesh.MeshPos;
import de.tomalbrc.dynamo.impl.util.StlExporter;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.Locale;

public class ChunkSectionCollisionShape {
    static final int CHUNK_SIZE = ModConfig.getInstance().chunkSize;

    public static ChunkMeshGenerator.MeshData buildMesh(MeshPos pos, boolean[][][] solid) {
        var mesh = ChunkMeshGenerator.generateSmoothedMesh(solid, 0,0,0);
        if (mesh.positions.capacity() == 0)
            return null;

        if (ModConfig.getInstance().exportMesh) {
            try {
                StlExporter.writeAsciiStl(String.format(Locale.US, "/tmp/section-%d-%d-%d.stl", pos.getX(), pos.getY(), pos.getZ()), "section", mesh.positions, mesh.indices, mesh.indices.capacity());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return mesh;
    }

    public static ChunkMeshGenerator.MeshData buildChunkCollisionShape(Level level, MeshPos pos) {
        int baseX = pos.minBlockX();
        int baseZ = pos.minBlockZ();
        int minY = pos.minBlockY();
        int maxY = pos.maxBlockY() + 1;
        int height = maxY - minY;
        assert height == CHUNK_SIZE;

        boolean mesh = ModConfig.getInstance().mesh;

        var additionalRad = mesh? 4 : 0;
        var additionalRadHalf = mesh? additionalRad/2 : 0;

        boolean hasSolid = false;
        var solid = new boolean[CHUNK_SIZE + additionalRad][CHUNK_SIZE + additionalRad][CHUNK_SIZE + additionalRad];
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
        for (int x = 0; x < CHUNK_SIZE + additionalRad; x++) {
            for (int y = 0; y < CHUNK_SIZE + additionalRad; y++) {
                for (int z = 0; z < CHUNK_SIZE + additionalRad; z++) {
                    tmp.set((baseX + x) - additionalRadHalf, (minY + y) - additionalRadHalf, (baseZ + z) - additionalRadHalf);
                    BlockState st = level.getBlockState(tmp);
                    boolean isSolid = !st.is(BlockTags.LEAVES) && !st.getCollisionShape(level, tmp).isEmpty();
                    solid[x][y][z] = isSolid;
                    hasSolid |= isSolid;
                }
            }
        }

        if (!hasSolid)
            return null;

        if (mesh) {
            return buildMesh(pos, solid);
        }

        return null;
    }
}

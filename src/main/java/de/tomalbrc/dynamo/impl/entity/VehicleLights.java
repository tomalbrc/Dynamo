package de.tomalbrc.dynamo.impl.entity;

import de.tomalbrc.dynamo.impl.config.vehicle.LightsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleLights {
    private final Map<BlockPos, BlockState> litBlocks = new ConcurrentHashMap<>();
    private final LightsConfig config;

    public VehicleLights(LightsConfig config) {
        this.config = config;
    }

    public List<Packet<? super ClientGamePacketListener>> rescan(Level level, net.minecraft.world.phys.Vec3 pos, Quaternionf q) {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();

        // restore old blocks
        for (var entry : litBlocks.entrySet()) {
            packets.add(new ClientboundBlockUpdatePacket(entry.getKey(), entry.getValue()));
        }
        litBlocks.clear();

        if (!config.enabled) return packets;

        // rotate each light offset by vehicle rotation
        Vector3f[] rotatedOffsets = config.lightPositions.stream()
                .map(offset -> new Vector3f(offset).rotate(q))
                .toArray(Vector3f[]::new);

        // rotate direction
        Vector3f dir = new Vector3f(config.lightDirection).rotate(q);

        for (int i = 0; i < config.maxDistance; i++) {
            for (Vector3f offset : rotatedOffsets) {
                Vector3f step = new Vector3f(dir).mul(i);
                Vector3f worldPos = new Vector3f(offset).add(step);
                var npos = pos.add(worldPos.x, worldPos.y, worldPos.z);
                BlockPos bp = BlockPos.containing(npos);
                BlockState state = level.getBlockState(bp);
                if (state.isAir() || state.is(Blocks.WATER)) {
                    litBlocks.put(bp, state);
                }
            }
        }

        for (Map.Entry<BlockPos, BlockState> entry : litBlocks.entrySet()) {
            BlockState lightState = Blocks.LIGHT.defaultBlockState()
                    .setValue(LightBlock.LEVEL, config.lightLevel);
            boolean waterlogged = entry.getValue().getFluidState().is(FluidTags.WATER);
            lightState = lightState.setValue(LightBlock.WATERLOGGED, waterlogged);

            packets.add(new ClientboundBlockUpdatePacket(entry.getKey(), lightState));
        }
        return packets;
    }

    public List<Packet<? super ClientGamePacketListener>> clear() {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        for (var entry : litBlocks.entrySet()) {
            packets.add(new ClientboundBlockUpdatePacket(entry.getKey(), entry.getValue()));
        }
        litBlocks.clear();
        return packets;
    }

    public boolean hasLightBlocks() {
        return !litBlocks.isEmpty();
    }
}
package de.tomalbrc.dynamo.impl.entity;

import de.tomalbrc.bil.core.holder.base.SimpleAnimatedHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CarLights {
    private final Map<BlockPos, BlockState> litBlocks = new ConcurrentHashMap<>();

    public List<Packet<? super @NotNull ClientGamePacketListener>> rescan(Level level, net.minecraft.world.phys.Vec3 pos, Quaternionf q) {
        List<Packet<? super @NotNull ClientGamePacketListener>> packets = new ArrayList<>();
        for (var entry : litBlocks.entrySet()) {
            packets.add(new ClientboundBlockUpdatePacket(entry.getKey(), entry.getValue()));
        }
        litBlocks.clear();

        Vector3f[] offsets = new Vector3f[2];
        offsets[0] = new Vector3f(1.7f,0.25f,2.6f).rotate(q);
        offsets[1] = new Vector3f(-1.7f,0.25f,2.6f).rotate(q);

        Vector3fc dir = new Vector3f(0,0,1).rotate(q);
        for (int i = 0; i < 20; i++) {
            for (Vector3f offset : offsets) {
                var no = dir.mul(i, new Vector3f()).add(offset);
                var npos = pos.add(no.x, no.y, no.z);
                var bp = BlockPos.containing(npos);
                var state = level.getBlockState(bp);
                if (state.isAir() || state.is(Blocks.WATER))
                    litBlocks.put(bp, state);
            }
        }

        for (Map.Entry<BlockPos, BlockState> entry : litBlocks.entrySet()) {
        packets.add(new ClientboundBlockUpdatePacket(entry.getKey(), Blocks.LIGHT.withPropertiesOf(entry.getValue()).setValue(LightBlock.WATERLOGGED, entry.getValue().getValueOrElse(LightBlock.WATERLOGGED, entry.getValue().getFluidState().is(FluidTags.WATER))).setValue(LightBlock.LEVEL, 13)));
        }

        return packets;
    }

    public List<Packet<? super @NotNull ClientGamePacketListener>> clear() {
        List<Packet<? super @NotNull ClientGamePacketListener>> packets = new ArrayList<>();
        for (var entry : litBlocks.entrySet()) {
            packets.add(new ClientboundBlockUpdatePacket(entry.getKey(), entry.getValue()));
        }
        return packets;
    }
}

package de.tomalbrc.dynamo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class ServerEvents {
    public static class Block {
        public static final Event<@NotNull BlockUpdate> BLOCK_UPDATE = EventFactory.createArrayBacked(BlockUpdate.class, (events) -> (level, pos, neighbourBlockState, neighbourPos) -> {
            for (var e : events) {
                e.onBlockUpdate(level, pos, neighbourBlockState, neighbourPos);
            }
        });

        @FunctionalInterface
        public interface BlockUpdate {
            void onBlockUpdate(Level level, BlockPos pos, BlockState blockState, BlockPos blockPos);
        }
    }
}
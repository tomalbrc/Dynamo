package de.tomalbrc.dynamo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.tomalbrc.dynamo.api.event.NoPositionSyncEntity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @WrapOperation(method = "sendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity$Synchronizer;sendToTrackingPlayers(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 4))
    private void dynamo$preventPosSync(ServerEntity.Synchronizer instance, Packet<? super ClientGamePacketListener> packet, Operation<Void> original) {
        if (this.entity instanceof NoPositionSyncEntity) {
            return;
        }

        original.call(instance, packet);
    }
}

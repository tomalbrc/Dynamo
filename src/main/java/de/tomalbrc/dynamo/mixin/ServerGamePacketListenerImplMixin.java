package de.tomalbrc.dynamo.mixin;

import de.tomalbrc.dynamo.impl.entity.CarEntity;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"), cancellable = true)
    private void dynamo$onAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        var swap = packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        if (swap && player.getVehicle() instanceof CarEntity carEntity) {
            carEntity.reset();
            ci.cancel();
        }
    }

    @Inject(method = "handleAnimate", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"), cancellable = true)
    private void dynamo$onAnimate(ServerboundSwingPacket serverboundSwingPacket, CallbackInfo ci) {
        if (player.getVehicle() instanceof CarEntity carEntity) {
            carEntity.toggleLights();
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"), cancellable = true)
    private void dynamo$onAnimate(ServerboundUseItemPacket serverboundUseItemPacket, CallbackInfo ci) {
        if (player.getVehicle() instanceof CarEntity carEntity) {
            carEntity.honk();
            ci.cancel();
        }
    }
}


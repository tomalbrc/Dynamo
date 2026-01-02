package de.tomalbrc.dynamo.mixin;

import de.tomalbrc.dynamo.impl.CarEntity;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
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
}


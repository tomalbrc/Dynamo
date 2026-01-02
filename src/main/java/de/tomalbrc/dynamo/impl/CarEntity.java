package de.tomalbrc.dynamo.impl;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import xyz.nucleoid.packettweaker.PacketContext;

public class CarEntity extends Minecart implements PolymerEntity {
    ElementHolder holder;

    public CarEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);

        var e = new ItemDisplayElement(Items.DIAMOND_BLOCK);

        this.holder = new ElementHolder();
        this.holder.addElement(e);
        EntityAttachment.ofTicking(holder, this);
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
        return EntityType.BLOCK_DISPLAY;
    }
}

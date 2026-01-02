package de.tomalbrc.dynamo.mixin;

import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import de.tomalbrc.dynamo.impl.world.DynamicWorldContainer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public class LevelMixin implements DynamicWorldContainer {
    @Unique private DynamicWorld space;

    @Unique
    @Override
    public void setDynamicWorld(DynamicWorld space) {
        this.space = space;
    }

    @Unique
    @Override
    public DynamicWorld getDynamicWorld() {
        return this.space;
    }
}

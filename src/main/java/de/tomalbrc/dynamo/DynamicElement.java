package de.tomalbrc.dynamo;

import com.jme3.bullet.objects.PhysicsBody;

import java.util.function.Consumer;

public record DynamicElement(PhysicsBody physicsBody, Consumer<DynamicElement> onUpdate, Consumer<DynamicElement> onRemove) {
    public void update() {
        onUpdate.accept(this);
    }

    public void remove() {
        onRemove.accept(this);
    }
}

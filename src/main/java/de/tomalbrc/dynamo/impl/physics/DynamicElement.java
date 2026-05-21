package de.tomalbrc.dynamo.impl.physics;

import java.util.function.Consumer;

public record DynamicElement(int physicsBody, Consumer<DynamicElement> onUpdate, Consumer<DynamicElement> onRemove) {
    public void update() {
        onUpdate.accept(this);
    }

    public void remove() {
        onRemove.accept(this);
    }
}

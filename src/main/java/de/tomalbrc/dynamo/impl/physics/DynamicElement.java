package de.tomalbrc.dynamo.impl.physics;

import com.github.stephengold.joltjni.Body;

import java.util.function.Consumer;

public record DynamicElement(int physicsBody, Consumer<DynamicElement> onUpdate, Consumer<DynamicElement> onRemove) {
    public void update() {
        onUpdate.accept(this);
    }

    public void remove() {
        onRemove.accept(this);
    }
}

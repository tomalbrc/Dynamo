package de.tomalbrc.dynamo.impl.config.vehicle;

import com.github.stephengold.joltjni.enumerate.ESpringMode;

public class SuspensionConfig {
    public float minLength = 0.1f;
    public float maxLength = 0.8f;

    public ESpringMode mode = ESpringMode.StiffnessAndDamping;
    public float stiffness = 4500.f;
    public float damping = .1f;
}

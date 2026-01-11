package de.tomalbrc.dynamo.impl.config.vehicle;

import com.github.stephengold.joltjni.enumerate.ESpringMode;

public class SuspensionConfig {
    public float minLength = 0.1f;
    public float maxLength = 1.f;

    public ESpringMode mode = ESpringMode.StiffnessAndDamping;
    public float stiffness = 5000.f;
    public float damping = .9f;
}

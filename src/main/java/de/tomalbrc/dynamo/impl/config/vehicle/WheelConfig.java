package de.tomalbrc.dynamo.impl.config.vehicle;

import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

public class WheelConfig {
    public float radius = 1.7f;
    public float width = 0.5f;
    public Identifier model = Identifier.fromNamespaceAndPath(Dynamo.MODID, "wheels");
    public SuspensionConfig suspension = new SuspensionConfig();

    public float maxBrakeTorque = 2500.0f;
    public float maxHandBrakeTorque = 4000.0f;
    public float maxSteerAngle = 40;

    public Vector3f offset = new Vector3f();

    public WheelConfig(Vector3f offset, float maxSteerAngle) {
        this.offset = offset;
        this.maxSteerAngle = maxSteerAngle;
    }
}

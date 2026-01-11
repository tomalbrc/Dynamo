package de.tomalbrc.dynamo.impl.config.vehicle;

import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.List;

public class VehicleConfig {
    public float halfWidth = 1.1f;
    public float halfHeight = 0.5f;
    public float halfLength = 2.5f;

    public float mass = 500;
    public float maxPitchRollAngle = 50;

    public EngineConfig engine = new EngineConfig();
    public TransmissionConfig transmission = new TransmissionConfig();
    public List<DifferentialConfig> differentials = List.of(new DifferentialConfig());
    public List<WheelConfig> wheels = List.of(
            new WheelConfig(new Vector3f(halfWidth + 0.1f, -0.2f, 1.6f), 30),
            new WheelConfig(new Vector3f(-halfWidth - 0.1f, -0.2f, 1.6f), 30),
            new WheelConfig(new Vector3f(halfWidth + 0.1f, -0.2f, -1.4f), 0),
            new WheelConfig(new Vector3f(-halfWidth - 0.1f, -0.2f, -1.4f), 0)
    );
    public String model;
}

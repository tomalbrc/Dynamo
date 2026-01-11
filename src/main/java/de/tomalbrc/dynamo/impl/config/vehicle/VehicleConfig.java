package de.tomalbrc.dynamo.impl.config.vehicle;

import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.List;

public class VehicleConfig {
    public float halfWidth = 1.1f;
    public float halfHeight = 0.5f;
    public float halfLength = 1.4f;

    public float maxPitchRollAngle = 60;

    public EngineConfig engine = new EngineConfig();
    public TransmissionConfig transmission = new TransmissionConfig();
    public List<DifferentialConfig> differentials = List.of(new DifferentialConfig());
    public List<WheelConfig> wheels = List.of(
            new WheelConfig(new Vector3f(halfWidth, -0.2f, 1.7f), 40),
            new WheelConfig(new Vector3f(-halfWidth, -0.2f, 1.7f), 40),
            new WheelConfig(new Vector3f(halfWidth, -0.2f, -1.4f), 0),
            new WheelConfig(new Vector3f(-halfWidth, -0.2f, -1.4f), 0)
    );
    public String model;
}

package de.tomalbrc.dynamo.impl.config.vehicle;

import org.joml.Vector3f;
import java.util.List;

public class CarLightsConfig {
    public boolean enabled = true;
    public boolean alwaysEnabled = false;
    public List<Vector3f> lightPositions = List.of(
        new Vector3f(1.7f, 0.25f, 2.6f),
        new Vector3f(-1.7f, 0.25f, 2.6f)
    );
    public Vector3f lightDirection = new Vector3f(0, 0, 1); // local forward rotated with vehicle
    public int maxDistance = 20;
    public int lightLevel = 13;
}
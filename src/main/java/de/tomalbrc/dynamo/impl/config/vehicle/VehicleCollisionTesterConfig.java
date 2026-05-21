package de.tomalbrc.dynamo.impl.config.vehicle;

public class VehicleCollisionTesterConfig {
    public Type type = Type.Cylinder;
    public float radius = 0.5f;

    public enum Type {
        Ray,
        Cylinder,
        Sphere
    }
}
package de.tomalbrc.dynamo.impl.config.vehicle;

public class DifferentialConfig {
    public int leftWheel = 3;
    public int rightWheel = 2;

    public float engineTorqueRatio = 1;
    public float differentialRatio = 4f;
    public float limitedSlipRatio = Float.MAX_VALUE;
}

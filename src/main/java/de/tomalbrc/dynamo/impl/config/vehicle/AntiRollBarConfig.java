package de.tomalbrc.dynamo.impl.config.vehicle;

public class AntiRollBarConfig {
    public int leftWheel = 0;
    public int rightWheel = 1;
    public float stiffness = 1000f;

    public AntiRollBarConfig(int leftWheel, int rightWheel, float stiffness) {
        this.leftWheel = leftWheel;
        this.rightWheel = rightWheel;
        this.stiffness = stiffness;
    }
}
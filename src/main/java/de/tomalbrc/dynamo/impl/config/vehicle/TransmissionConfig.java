package de.tomalbrc.dynamo.impl.config.vehicle;

import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import it.unimi.dsi.fastutil.floats.FloatList;

public class TransmissionConfig {
    public ETransmissionMode mode = ETransmissionMode.Auto;
    public FloatList gearRations = FloatList.of(0.8f);
}

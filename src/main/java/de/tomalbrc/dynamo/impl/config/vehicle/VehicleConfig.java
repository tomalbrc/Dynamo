package de.tomalbrc.dynamo.impl.config.vehicle;

import com.google.gson.annotations.SerializedName;
import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector3f;

import java.util.List;

public class VehicleConfig {
    public Identifier id = Identifier.fromNamespaceAndPath(Dynamo.MODID, "car");
    public boolean motorcycle = false;

    public float halfWidth = 1.1f;
    public float halfHeight = 0.5f;
    public float halfLength = 2.5f;

    public Vector3f centerOfMass = new Vector3f(0, -3.f, 0);

    public float friction = 1f;
    public float mass = 600;
    public float maxPitchRollAngle = 50;

    public boolean canBoost = true;
    public float boostForce = 4000f;

    public boolean canHonk = true;
    public Identifier honkSound = SoundEvents.WIND_CHARGE_BURST.value().location();

    public boolean canReset = true;
    public int resetCooldown = 120;

    public boolean fireResistant = false;

    public LeaningConfig leaning = new LeaningConfig();
    public VehicleCollisionTesterConfig collisionTester = new VehicleCollisionTesterConfig();

    public LightsConfig lights = new LightsConfig();

    public EngineConfig engine = new EngineConfig();
    public TransmissionConfig transmission = new TransmissionConfig();
    public List<DifferentialConfig> differentials = List.of(new DifferentialConfig());
    public List<WheelConfig> wheels = List.of(
            new WheelConfig(new Vector3f(halfWidth + 0.32f, -0.2f, 1.5f), 30),
            new WheelConfig(new Vector3f(-halfWidth - 0.32f, -0.2f, 1.5f), 30),
            new WheelConfig(new Vector3f(halfWidth + 0.32f, -0.2f, -1.4f), 0),
            new WheelConfig(new Vector3f(-halfWidth - 0.32f, -0.2f, -1.4f), 0)
    );

    @SerializedName("anti-roll-bars")
    public List<AntiRollBarConfig> antiRollBars = List.of(
            new AntiRollBarConfig(3, 2, 5000f),
            new AntiRollBarConfig(1, 0, 5000f)
    );

    public String model = "car";
}

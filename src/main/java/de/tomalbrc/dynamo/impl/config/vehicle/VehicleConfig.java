package de.tomalbrc.dynamo.impl.config.vehicle;

import com.google.gson.annotations.SerializedName;
import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import java.util.List;

public class VehicleConfig {
    public Identifier id = Identifier.fromNamespaceAndPath(Dynamo.MODID, "car");
    public ItemStackTemplate drop = new ItemStackTemplate(Items.STONE, 5);

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

    public CarLightsConfig lights = new CarLightsConfig();

    public EngineConfig engine = new EngineConfig();
    public TransmissionConfig transmission = new TransmissionConfig();
    public List<DifferentialConfig> differentials = List.of(new DifferentialConfig());
    public List<WheelConfig> wheels = List.of(
            new WheelConfig(new Vector3f(halfWidth + 0.32f, -0.2f, 1.5f), 30),
            new WheelConfig(new Vector3f(-halfWidth - 0.32f, -0.2f, 1.5f), 30),
            new WheelConfig(new Vector3f(halfWidth + 0.32f, -0.2f, -1.4f), 0),
            new WheelConfig(new Vector3f(-halfWidth - 0.32f, -0.2f, -1.4f), 0)
    );

    @SerializedName("anti_roll_bars")
    public List<AntiRollBarConfig> antiRollBars = List.of(
            new AntiRollBarConfig(3, 2, 5000f),
            new AntiRollBarConfig(1, 0, 5000f)
    );

    public String model = "car";
}

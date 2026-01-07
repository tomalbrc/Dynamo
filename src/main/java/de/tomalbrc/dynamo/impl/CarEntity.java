package de.tomalbrc.dynamo.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import de.tomalbrc.dynamo.api.event.NoPositionSyncEntity;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.util.Util;
import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import de.tomalbrc.dynamo.impl.world.DynamicWorldContainer;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.polymer.virtualentity.api.tracker.EntityTrackedData;
import eu.pb4.polymer.virtualentity.mixin.accessors.DisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;

public class CarEntity extends Entity implements PolymerEntity, NoPositionSyncEntity {
    public static Identifier ID = Util.id("car");

    float halfWidth = 1.1f;
    float halfHeight = 0.1f;
    float halfLength = 1.4f;

    ElementHolder holder;
    DisplayElement chassis;
    List<DisplayElement> wheels = new ArrayList<>();

    DynamicWorld world;
    VehicleConstraint vehicle;
    int vehicleBodyId;
    DynamicElement loader;

    private static final float BOOST_STRENGTH = 4000.0f;
    private boolean isBoosting = false;

    public CarEntity(EntityType<? extends @NotNull Entity> entityType, Level level) {
        super(entityType, level);

        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvisible(true);

        chassis = new ItemDisplayElement(Items.DIAMOND_BLOCK);
        chassis.setTeleportDuration(3);
        chassis.setInterpolationDuration(2);
        chassis.setScale(new Vector3f(halfWidth, halfHeight, halfLength).mul(2.f));
        chassis.ignorePositionUpdates();

        this.holder = new ElementHolder();
        this.holder.addElement(chassis);
        EntityAttachment.ofTicking(holder, this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {

    }

    public void setBoosting(boolean active) {
        this.isBoosting = active;
    }

    private void applyBoost() {
        if (isBoosting && this.vehicle != null) {
            BodyInterface bi = world.getPhysicsSystem().getBodyInterface();
            int chassisId = this.vehicle.getVehicleBody().getId();

            Quat rotation = bi.getRotation(chassisId);
            Mat44 rotationMatrix = Mat44.sRotation(rotation);

            var localForward = new com.github.stephengold.joltjni.Vec3(0, 0, 1);
            var boostForce = rotationMatrix.multiply3x3(localForward);

            boostForce.scaleInPlace(BOOST_STRENGTH);
            bi.addForce(chassisId, boostForce);
        }
    }


    @Override
    public void onRemoval(@NotNull RemovalReason removalReason) {
        super.onRemoval(removalReason);

        if (vehicle != null) {
            var world = this.getPhysicsWorld();
            world.getPhysicsSystem().getBodyInterface().removeBody(vehicleBodyId);
        }
    }

    public DynamicWorld getPhysicsWorld() {
        return ((DynamicWorldContainer) level()).getDynamicWorld();
    }

    public void setWorld(DynamicWorld world) {
        this.world = world;

        if (this.vehicle == null) {
            BoxShapeSettings boxSettings = new BoxShapeSettings(new com.github.stephengold.joltjni.Vec3(halfWidth, halfHeight, halfLength));

            float centerOfMassY = -1.5f;
            OffsetCenterOfMassShapeSettings chassisSettings = new OffsetCenterOfMassShapeSettings(new com.github.stephengold.joltjni.Vec3(0, centerOfMassY, 0), boxSettings);

            BodyCreationSettings bodySettings = new BodyCreationSettings()
                    .setShapeSettings(chassisSettings)
                    .setPosition(new RVec3(getX(), getY(), getZ()))
                    .setObjectLayer(DynamicWorld.objLayerMoving)
                    .setMotionType(EMotionType.Dynamic)
                    .setMotionQuality(EMotionQuality.Discrete)
                    .setFriction(0f);

            bodySettings.setMassPropertiesOverride(new MassProperties()); // Set mass props
            bodySettings.getMassPropertiesOverride().setMass(1500f);

            Body chassisBody = world.getPhysicsSystem().getBodyInterface().createBody(bodySettings);
            world.getPhysicsSystem().getBodyInterface().addBody(chassisBody.getId(), EActivation.Activate);

            VehicleConstraintSettings vehicleSettings = new VehicleConstraintSettings();

            float xOffset = halfWidth * 1.1f;
            float frontAxleZ = halfLength * 1.2f;
            float rearAxleZ = -halfLength * 1.01f;
            float yWheelPos = -0.2f;
            float radius = 1.4f;
            float width = 0.3f;

            vehicleSettings.addWheels(createWheel(xOffset, yWheelPos, frontAxleZ, radius, width, true));
            vehicleSettings.addWheels(createWheel(-xOffset, yWheelPos, frontAxleZ, radius, width, true));
            vehicleSettings.addWheels(createWheel(xOffset, yWheelPos, rearAxleZ, radius, width, false));
            vehicleSettings.addWheels(createWheel(-xOffset, yWheelPos, rearAxleZ, radius, width, false));

            WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();
            controllerSettings.getEngine().setMinRpm(4000);
            controllerSettings.getEngine().setMaxRpm(8000);
            controllerSettings.getEngine().setMaxTorque(10000);
            controllerSettings.getTransmission().setMode(ETransmissionMode.Auto);
            controllerSettings.getTransmission().setClutchStrength(1.5f);
            controllerSettings.setNumDifferentials(1);
            vehicleSettings.setController(controllerSettings);

            VehicleDifferentialSettings vds = controllerSettings.getDifferential(0);
            vds.setLeftWheel(2);
            vds.setRightWheel(3);

            var tester = new VehicleCollisionTesterRay(DynamicWorld.objLayerMoving);
            this.vehicle = new VehicleConstraint(chassisBody, vehicleSettings);
            vehicle.setMaxPitchRollAngle(Mth.DEG_TO_RAD*60);
            this.vehicle.setVehicleCollisionTester(tester);
            this.vehicleBodyId = chassisBody.getId();

            world.getPhysicsSystem().addConstraint(this.vehicle);
            world.getPhysicsSystem().addStepListener(this.vehicle.getStepListener());

            for (int i = 0; i < 4; i++) {
                var e = new ItemDisplayElement(Items.DIAMOND_BLOCK);
                e.setScale(new Vector3f(width, radius + 0.2f, radius + 0.2f));
                e.setTranslation(new Vector3f(0, -0.25f, 0));
                e.setTeleportDuration(3);
                e.setInterpolationDuration(2);
                e.ignorePositionUpdates();
                this.wheels.add(e);
                this.holder.addElement(e);
            }
        }
    }

    private WheelSettingsWv createWheel(float x, float y, float z, float radius, float width, boolean front) {
        WheelSettingsWv w = new WheelSettingsWv();
        w.setPosition(new com.github.stephengold.joltjni.Vec3(x, y, z));
        w.setRadius(radius);
        w.setWidth(width);

        w.setSuspensionMinLength(0.05f);
        w.setSuspensionMaxLength(1.0f);

        w.getSuspensionSpring().setMode(ESpringMode.StiffnessAndDamping);
        w.getSuspensionSpring().setStiffness(4000.5f);
        w.getSuspensionSpring().setDamping(.1f);

        if (!front) {
            w.setMaxSteerAngle(0f);
        } else {
            w.setMaxSteerAngle(Mth.DEG_TO_RAD * 40);
        }

        w.setMaxBrakeTorque(2500.0f);
        w.setMaxHandBrakeTorque(4000.0f);

        return w;
    }

    @Override
    public void modifyRawEntityAttributeData(List<ClientboundUpdateAttributesPacket.AttributeSnapshot> data, ServerPlayer player, boolean initial) {
        data.add(new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.CAMERA_DISTANCE, 16f, List.of()));
        PolymerEntity.super.modifyRawEntityAttributeData(data, player, initial);
    }

    @Override
    public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
        //var flag = setFlag((byte)1, EntityTrackedData.INVISIBLE_FLAG_INDEX, true);
        //data.add(new SynchedEntityData.DataValue<>(LivingEntityAccessor.getDATA_LIVING_ENTITY_FLAGS().id(), LivingEntityAccessor.getDATA_LIVING_ENTITY_FLAGS().serializer(), flag));
        data.add(new SynchedEntityData.DataValue<>(EntityTrackedData.SILENT.id(), EntityTrackedData.SILENT.serializer(), true));
        data.add(new SynchedEntityData.DataValue<>(DisplayAccessor.getDATA_POS_ROT_INTERPOLATION_DURATION_ID().id(), DisplayAccessor.getDATA_POS_ROT_INTERPOLATION_DURATION_ID().serializer(), 2));
        PolymerEntity.super.modifyRawTrackedData(data, player, initial);
    }

    @Override
    public void startSeenByPlayer(@NotNull ServerPlayer serverPlayer) {
        super.startSeenByPlayer(serverPlayer);

        serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), new MobEffectInstance(MobEffects.WATER_BREATHING, -1, 0, false, false), false));
        serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false), false));
    }

    protected byte setFlag(byte b, int index, boolean value) {
        if (value) {
            return (byte) (b | 1 << index);
        } else {
            return (byte) (b & ~(1 << index));
        }
    }

    @Override
    public @NotNull InteractionResult interactAt(Player player, @NotNull Vec3 vec3, @NotNull InteractionHand interactionHand) {
        player.startRiding(this);
        return super.interact(player, interactionHand);
    }

    @Override
    public void baseTick() {
        super.baseTick();

        boolean skip = this.tickCount % 2 == 1;
        if (this.vehicle != null) {

            if (this.isVehicle() && this.getFirstPassenger() instanceof ServerPlayer player) {
                this.handleInput(player, player.getLastClientInput());
            } else {
                WheeledVehicleController controller = (WheeledVehicleController) this.vehicle.getController();
                controller.setDriverInput(0, 0, 0.1f, 0);
            }

            if (skip)
                return;

            for (int i = 0; i < 4; i++) {
                RVec3 wheelPos = new RVec3();
                Quat wheelRot = new Quat();
                this.vehicle.getWheelPositionAndRotation(i, com.github.stephengold.joltjni.Vec3.sAxisX(), com.github.stephengold.joltjni.Vec3.sAxisY(), wheelPos, wheelRot);

                var element = this.wheels.get(i);
                element.setOverridePos(new Vec3(wheelPos.x(), wheelPos.y(), wheelPos.z()));
                element.setLeftRotation(new Quaternionf(wheelRot.getX(), wheelRot.getY(), wheelRot.getZ(), wheelRot.getW()));
                element.startInterpolationIfDirty();
            }

            BodyInterface bi = world.getPhysicsSystem().getBodyInterface();

            var chassisBody = this.vehicle.getVehicleBody().getId();
            RVec3 carPos = new RVec3();
            carPos.loadZero();
            Quat carRot = new Quat();
            carRot.loadIdentity();
            bi.getPositionAndRotation(chassisBody, carPos, carRot);

            chassis.setOverridePos(new Vec3(carPos.xx(), carPos.yy(), carPos.zz()));
            chassis.setLeftRotation(new Quaternionf(carRot.getX(), carRot.getY(), carRot.getZ(), carRot.getW()));
            chassis.startInterpolationIfDirty();

            this.setPos(carPos.xx(), carPos.yy(), carPos.zz());

            updatePos();
        }
    }

    public void updatePos() {
        List<Packet<? super @NotNull ClientGamePacketListener>> packets = new ArrayList<>();
        var pos1 = (new ClientboundEntityPositionSyncPacket(this.getId(), new PositionMoveRotation(chassis.getCurrentPos(), chassis.getCurrentPos(), 0f, 0f), false));
        var pos2 = (new ClientboundEntityPositionSyncPacket(this.chassis.getEntityId(), new PositionMoveRotation(chassis.getCurrentPos(), chassis.getCurrentPos(), 0f, 0f), false));
        packets.add(pos1);
        packets.add(pos2);

        for (VirtualElement element : this.holder.getElements()) {
            if (element instanceof GenericEntityElement entityElement) {
                var id = entityElement.getEntityId();
                packets.add(new ClientboundEntityPositionSyncPacket(id, new PositionMoveRotation(element.getCurrentPos(), element.getCurrentPos(), 0f, 0f), false));
            }
        }
        this.holder.sendPacket(new ClientboundBundlePacket(packets));
    }

    public void handleInput(ServerPlayer player, Input input) {
        WheeledVehicleController controller = (WheeledVehicleController) this.vehicle.getController();
        float forward = 0;
        float steering = 0;
        float handBrake = 0;

        if (input.forward()) forward = 1.0f;
        if (input.backward()) {
            forward = -1.0f;
        }

        if (input.left()) steering = -1.0f;
        if (input.right()) steering = 1.0f;
        if (input.jump()) handBrake = 1.0f;

        float brake = (forward == 0f) ? 1.0f : 0.0f;

        var bi = world.getPhysicsSystem().getBodyInterface();
        bi.activateBody(vehicleBodyId);
        controller.setDriverInput(forward, steering, brake, handBrake);

        if (input.jump()) {
            for (int i = 0; i < 4; i++) {
                RVec3 wheelPos = new RVec3();
                Quat wheelRot = new Quat();
                this.vehicle.getWheelPositionAndRotation(i, com.github.stephengold.joltjni.Vec3.sAxisX(), com.github.stephengold.joltjni.Vec3.sAxisY(), wheelPos, wheelRot);

                var state = level().getBlockState(BlockPos.containing(wheelPos.x(), wheelPos.y() - 1.25, wheelPos.z()));
                if (state.isSolid()) {
                    var rad = 0.25f;
                    ((ServerLevel) level()).sendParticles(new DustParticleOptions(0xFFFFFFFF, 1), wheelPos.x(), wheelPos.y(), wheelPos.z(), 10, rad, rad, rad, 0.11);
                }
            }
        }

        this.setBoosting(player.getLastClientInput().sprint());
        if (isBoosting) {
            var axle = bi.getPosition(this.vehicleBodyId);
            var vel = bi.getLinearVelocity(this.vehicleBodyId);
            vel.scaleInPlace(0.1f);

            var rad = 0.25f;
            ((ServerLevel) level()).sendParticles(ParticleTypes.COPPER_FIRE_FLAME, axle.x() - vel.getX(), axle.y() - vel.getY(), axle.z() - vel.getZ(), 10, rad, rad, rad, 0.11);
        }

        this.applyBoost();
    }

    @Override
    public boolean hurtServer(@NotNull ServerLevel serverLevel, @NotNull DamageSource damageSource, float f) {
        return damageSource.is(DamageTypeTags.IS_PLAYER_ATTACK) || !damageSource.is(DamageTypeTags.IS_FALL);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput valueInput) {

    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput valueOutput) {

    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
        return EntityType.SLIME;
    }

    public void reset() {
        var pos = getPhysicsWorld().getPhysicsSystem().getBodyInterface().getPosition(vehicleBodyId);
        pos.addInPlace(0,1,0);

        getPhysicsWorld().getPhysicsSystem().getBodyInterface().setPosition(vehicleBodyId, pos, EActivation.Activate);
        getPhysicsWorld().getPhysicsSystem().getBodyInterface().setAngularVelocity(vehicleBodyId, com.github.stephengold.joltjni.Vec3.sZero());
        getPhysicsWorld().getPhysicsSystem().getBodyInterface().setLinearVelocity(vehicleBodyId, com.github.stephengold.joltjni.Vec3.sZero());
    }
}

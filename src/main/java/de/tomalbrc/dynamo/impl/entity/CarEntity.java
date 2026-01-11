package de.tomalbrc.dynamo.impl.entity;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import de.tomalbrc.bil.core.holder.base.SimpleAnimatedHolder;
import de.tomalbrc.bil.core.holder.wrapper.Bone;
import de.tomalbrc.dynamo.api.event.NoPositionSyncEntity;
import de.tomalbrc.dynamo.impl.config.vehicle.VehicleConfig;
import de.tomalbrc.dynamo.impl.config.vehicle.WheelConfig;
import de.tomalbrc.dynamo.impl.model.Models;
import de.tomalbrc.dynamo.impl.util.Util;
import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import de.tomalbrc.dynamo.impl.world.DynamicWorldContainer;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.polymer.virtualentity.api.tracker.EntityTrackedData;
import eu.pb4.polymer.virtualentity.mixin.accessors.DisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;

public class CarEntity extends Entity implements PolymerEntity, NoPositionSyncEntity {
    public static Identifier ID = Util.id("car");
    private static final float BOOST_STRENGTH = 4000.0f;

    SimpleAnimatedHolder holder;
    DisplayElement chassis;
    List<DisplayElement> wheels = new ArrayList<>();

    DynamicWorld world;
    VehicleConstraint vehicleConstraint;
    int vehicleBodyId;

    boolean isBoosting = false;

    VehicleConfig config = new VehicleConfig();

    CarLights carLights = new CarLights();

    public CarEntity(EntityType<? extends @NotNull Entity> entityType, Level level) {
        super(entityType, level);

        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvisible(true);

        var item = Items.DIAMOND_BLOCK.getDefaultInstance();
        item.set(DataComponents.ITEM_MODEL, Items.AIR.components().get(DataComponents.ITEM_MODEL));
        this.chassis = new ItemDisplayElement(item);
        this.chassis.setTeleportDuration(3);
        this.chassis.setInterpolationDuration(2);
        this.chassis.setScale(new Vector3f(this.config.halfWidth, this.config.halfHeight, this.config.halfLength).mul(2.f));
        this.chassis.ignorePositionUpdates();

        this.holder = new SimpleAnimatedHolder(Models.get("car112"));
        this.holder.addElement(this.chassis);
        EntityAttachment.ofTicking(holder, this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {

    }

    public void setBoosting(boolean active) {
        this.isBoosting = active;
    }

    private void applyBoost() {
        if (isBoosting && this.vehicleConstraint != null) {
            BodyInterface bi = world.getPhysicsSystem().getBodyInterface();
            int chassisId = this.vehicleConstraint.getVehicleBody().getId();

            Quat rotation = bi.getRotation(chassisId);
            Mat44 rotationMatrix = Mat44.sRotation(rotation);

            var boostForce = rotationMatrix.multiply3x3(com.github.stephengold.joltjni.Vec3.sAxisZ());
            boostForce.scaleInPlace(BOOST_STRENGTH);
            bi.addForce(chassisId, boostForce);

            rotationMatrix.close();
        }
    }


    @Override
    public void onRemoval(@NotNull RemovalReason removalReason) {
        super.onRemoval(removalReason);

        this.holder.sendPacket(new ClientboundBundlePacket(this.carLights.clear()));

        if (vehicleConstraint != null) {
            var world = this.getPhysicsWorld();
            world.getPhysicsSystem().getBodyInterface().removeBody(vehicleBodyId);
        }
    }

    public DynamicWorld getPhysicsWorld() {
        return ((DynamicWorldContainer) level()).getDynamicWorld();
    }

    public void setWorld(DynamicWorld world) {
        this.world = world;

        if (this.vehicleConstraint == null) {
            setupVehicle(world);
        }
    }

    private void setupVehicle(DynamicWorld world) {
        ShapeSettings chassisSettings = createShapeSettings();

        BodyCreationSettings bodySettings = new BodyCreationSettings()
                .setShapeSettings(chassisSettings)
                .setPosition(new RVec3(getX(), getY(), getZ()))
                .setObjectLayer(DynamicWorld.objLayerMoving)
                .setMotionType(EMotionType.Dynamic)
                .setFriction(1f);

        bodySettings.setMassPropertiesOverride(new MassProperties());
        bodySettings.getMassPropertiesOverride().setMass(config.mass);
        bodySettings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

        Body chassisBody = world.getPhysicsSystem().getBodyInterface().createBody(bodySettings);
        world.getPhysicsSystem().getBodyInterface().addBody(chassisBody.getId(), EActivation.Activate);

        VehicleConstraintSettings constraintSettings = new VehicleConstraintSettings();
        setupWheels(constraintSettings);

//            constraintSettings.setNumAntiRollBars(1);
//            var rb = constraintSettings.getAntiRollBar(0);
//            rb.setLeftWheel(2);
//            rb.setLeftWheel(3);

        WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();
        controllerSettings.getEngine().setMinRpm(config.engine.minRpm);
        controllerSettings.getEngine().setMaxRpm(config.engine.maxRpm);
        controllerSettings.getEngine().setMaxTorque(config.engine.maxTorque);
        controllerSettings.getTransmission().setMode(config.transmission.mode);
        controllerSettings.getTransmission().setGearRatios(config.transmission.gearRations.toFloatArray());
        setupDifferentials(controllerSettings);

        constraintSettings.setController(controllerSettings);

        VehicleCollisionTester collisionTester = new VehicleCollisionTesterCastCylinder(DynamicWorld.objLayerMoving);

        this.vehicleConstraint = new VehicleConstraint(chassisBody, constraintSettings);
        this.vehicleConstraint.setMaxPitchRollAngle(Mth.DEG_TO_RAD * config.maxPitchRollAngle);
        this.vehicleConstraint.setVehicleCollisionTester(collisionTester);
        this.vehicleBodyId = chassisBody.getId();

        world.getPhysicsSystem().addConstraint(this.vehicleConstraint);
        world.getPhysicsSystem().addStepListener(this.vehicleConstraint.getStepListener());
    }

    private @NotNull ShapeSettings createShapeSettings() {
        BoxShapeSettings boxSettings = new BoxShapeSettings(new com.github.stephengold.joltjni.Vec3(this.config.halfWidth, this.config.halfHeight, this.config.halfLength));

        CompoundShapeSettings compoundShapeSettings = new StaticCompoundShapeSettings();
        compoundShapeSettings.addShape(0, config.halfHeight, 0, boxSettings);

        return new OffsetCenterOfMassShapeSettings(new com.github.stephengold.joltjni.Vec3(config.centerOfMass.x, config.centerOfMass.y, config.centerOfMass.z), compoundShapeSettings);
    }

    private void setupWheels(VehicleConstraintSettings constraintSettings) {
        for (WheelConfig wheel : config.wheels) {
            var wheelWv = createWheel(wheel);
            constraintSettings.addWheels(wheelWv);

            var item = Items.DIAMOND_BLOCK.getDefaultInstance();
            item.set(DataComponents.ITEM_MODEL, wheel.model);

            var e = new ItemDisplayElement(item);
            e.setScale(new Vector3f(wheel.width + 0.4f, wheel.radius + 0.6f, wheel.radius + 0.6f));
            e.setTranslation(new Vector3f(0, -0.5f, 0));
            e.setTeleportDuration(3);
            e.setInterpolationDuration(3);
            e.ignorePositionUpdates();
            this.wheels.add(e);
            this.holder.addElement(e);
        }
    }

    private void setupDifferentials(WheeledVehicleControllerSettings controllerSettings) {
        controllerSettings.setNumDifferentials(config.differentials.size());

        for (int i = 0; i < config.differentials.size(); i++) {
            var diff = config.differentials.get(i);

            VehicleDifferentialSettings vds = controllerSettings.getDifferential(i);
            vds.setLeftWheel(diff.leftWheel);
            vds.setRightWheel(diff.rightWheel);
            vds.setEngineTorqueRatio(diff.engineTorqueRatio);
            vds.setDifferentialRatio(diff.differentialRatio);
            vds.setLimitedSlipRatio(diff.limitedSlipRatio);
        }
    }

    private WheelSettingsWv createWheel(WheelConfig wheelConfig) {
        WheelSettingsWv w = new WheelSettingsWv();
        w.setPosition(new com.github.stephengold.joltjni.Vec3(wheelConfig.offset.x, wheelConfig.offset.y, wheelConfig.offset.z));
        w.setRadius(wheelConfig.radius);
        w.setWidth(wheelConfig.width);
        w.setMaxBrakeTorque(wheelConfig.maxBrakeTorque);
        w.setMaxHandBrakeTorque(wheelConfig.maxHandBrakeTorque);

        w.setSuspensionMinLength(wheelConfig.suspension.minLength);
        w.setSuspensionMaxLength(wheelConfig.suspension.maxLength);

        w.getSuspensionSpring().setMode(wheelConfig.suspension.mode);
        w.getSuspensionSpring().setStiffness(wheelConfig.suspension.stiffness);
        w.getSuspensionSpring().setDamping(wheelConfig.suspension.damping);

        w.setMaxSteerAngle(Mth.DEG_TO_RAD * wheelConfig.maxSteerAngle);

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
        if (this.vehicleConstraint != null) {

            if (this.isVehicle() && this.getFirstPassenger() instanceof ServerPlayer player) {
                this.handleInput(player, player.getLastClientInput());
            } else {
                WheeledVehicleController controller = (WheeledVehicleController) this.vehicleConstraint.getController();
                controller.setDriverInput(0.001f, 0, 10f, 0);
            }

            if (skip)
                return;

            for (int i = 0; i < 4; i++) {
                RVec3 wheelPos = new RVec3();
                Quat wheelRot = new Quat();
                this.vehicleConstraint.getWheelPositionAndRotation(i, com.github.stephengold.joltjni.Vec3.sAxisX(), com.github.stephengold.joltjni.Vec3.sAxisY(), wheelPos, wheelRot);

                var element = this.wheels.get(i);
                element.setOverridePos(new Vec3(wheelPos.x(), wheelPos.y(), wheelPos.z()));
                element.setLeftRotation(new Quaternionf(wheelRot.getX(), wheelRot.getY(), wheelRot.getZ(), wheelRot.getW()));
                element.startInterpolationIfDirty();
            }

            BodyInterface bi = world.getPhysicsSystem().getBodyInterface();

            var chassisBody = this.vehicleConstraint.getVehicleBody().getId();
            RVec3 carPos = new RVec3();
            carPos.loadZero();
            Quat carRot = new Quat();
            carRot.loadIdentity();
            bi.getPositionAndRotation(chassisBody, carPos, carRot);

            var jQuat = new Quaternionf(carRot.getX(), carRot.getY(), carRot.getZ(), carRot.getW());

            this.chassis.setOverridePos(new Vec3(carPos.xx(), carPos.yy(), carPos.zz()));
            this.chassis.setLeftRotation(jQuat);
            this.chassis.startInterpolationIfDirty();

            var carPosVec = new Vec3(carPos.xx(), carPos.yy(), carPos.zz());
            for (Bone<?> bone : this.holder.getBones()) {
                var mmm = new Matrix4f().rotate(jQuat);

                var mat = mmm.mul(bone.getDefaultPose().matrix());
                bone.element().setTransformation(null, mat);
                if (bone.element() instanceof GenericEntityElement entityElement) {
                    entityElement.ignorePositionUpdates();
                    entityElement.setOverridePos(carPosVec);
                }

                bone.element().setInterpolationDuration(null, 3);
                bone.element().setTeleportDuration(null, 3);
                bone.element().startInterpolationIfDirty(null);
            }

            this.setPos(carPosVec);

            updatePos(jQuat);
        }
    }

    public void updatePos(Quaternionf quat) {
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

        var np = carLights.rescan(level(), chassis.getCurrentPos(), quat);
        packets.addAll(np);

        this.holder.sendPacket(new ClientboundBundlePacket(packets));
    }

    public void handleInput(ServerPlayer player, Input input) {
        WheeledVehicleController controller = (WheeledVehicleController) this.vehicleConstraint.getController();
        float forward = 0.01f;
        float steering = 0;
        float handBrake = 0;

        if (input.forward()) forward = 1.0f;
        if (input.backward()) {
            forward = -1.0f;
        }

        if (input.left()) steering = -1.0f;
        if (input.right()) steering = 1.0f;
        if (input.jump()) handBrake = 1.0f;

        float brake = (forward == 0.01f) ? 1.0f : 0.0f;

        var bi = world.getPhysicsSystem().getBodyInterface();
        bi.activateBody(vehicleBodyId);
        controller.setDriverInput(forward * 0.8f, steering * 0.8f, brake, handBrake);

        if (input.jump()) {
            for (int i = 0; i < 4; i++) {
                RVec3 wheelPos = new RVec3();
                Quat wheelRot = new Quat();
                this.vehicleConstraint.getWheelPositionAndRotation(i, com.github.stephengold.joltjni.Vec3.sAxisX(), com.github.stephengold.joltjni.Vec3.sAxisY(), wheelPos, wheelRot);

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
        pos.addInPlace(0, 1, 0);

        getPhysicsWorld().getPhysicsSystem().getBodyInterface().setPosition(vehicleBodyId, pos, EActivation.Activate);
        getPhysicsWorld().getPhysicsSystem().getBodyInterface().setAngularVelocity(vehicleBodyId, com.github.stephengold.joltjni.Vec3.sZero());
        getPhysicsWorld().getPhysicsSystem().getBodyInterface().setLinearVelocity(vehicleBodyId, com.github.stephengold.joltjni.Vec3.sZero());
    }
}

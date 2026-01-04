package de.tomalbrc.dynamo.impl;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsVehicle;
import com.jme3.math.Quaternion;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.util.Util;
import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
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

public class CarEntity extends Entity implements PolymerEntity {
    public static Identifier ID = Util.id("car");

    float halfWidth = 0.8f;
    float halfHeight = 0.1f;
    float halfLength = 1.f;

    ElementHolder holder;
    ItemDisplayElement chassis;
    List<ItemDisplayElement> wheels = new ArrayList<>();

    DynamicWorld world;
    PhysicsVehicle vehicle;
    DynamicElement loader;

    private static final float BOOST_STRENGTH = 4000.0f;
    private boolean isBoosting = false;

    public CarEntity(EntityType<? extends @NotNull Entity> entityType, Level level) {
        super(entityType, level);

        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvisible(true);

        chassis = new ItemDisplayElement(Items.DIAMOND_BLOCK);
        chassis.setTranslation(new Vector3f(0,-0.25f,0));
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
        if (isBoosting) {
            com.jme3.math.Vector3f forward = vehicle.getForwardVector(null);
            com.jme3.math.Vector3f boostForce = forward.mult(BOOST_STRENGTH);
            vehicle.applyCentralForce(boostForce);
            //vehicle.applyCentralForce(new com.jme3.math.Vector3f(0, -100f, 0));
        }
    }


    @Override
    public void onRemoval(@NotNull RemovalReason removalReason) {
        super.onRemoval(removalReason);

        if (this.world != null) this.world.getPhysicsThread().enqueue(space -> space.remove(this.vehicle));
    }

    public void setWorld(DynamicWorld world) {
        this.world = world;
        if (this.vehicle == null) {
            CollisionShape boxShape = new BoxCollisionShape(new com.jme3.math.Vector3f(halfWidth, halfHeight, halfLength));
            float centerOfMass = 0.7f;

            CompoundCollisionShape chassisShape = new CompoundCollisionShape();
            com.jme3.math.Vector3f boxLocalPos = new com.jme3.math.Vector3f(0, centerOfMass, 0);
            chassisShape.addChildShape(boxShape, boxLocalPos);

            float mass = 100f;
            PhysicsVehicle vehicle = new PhysicsVehicle(chassisShape, mass);
            vehicle.setSuspensionStiffness(20f);
            vehicle.setSuspensionCompression(10.0f);
            vehicle.setSuspensionDamping(0.2f);
            vehicle.setFrictionSlip(10.0f);
            vehicle.setRestitution(0.f);
            vehicle.setRollingFriction(1f);
            vehicle.setFriction(1f);

            vehicle.setAngularDamping(0.6f);
            vehicle.setAngularFactor(1f);

            float yWheelPos = centerOfMass / 2.f;

            boolean front = true;
            boolean rear = false;
            float xOffset = halfWidth * 1.1f;
            float frontAxleZ = halfLength * 1.2f;
            float rearAxleZ = -halfLength * 1.01f;
            float radius = .5f;
            float restLength = 0.3f;

            com.jme3.math.Vector3f axleDirection = new com.jme3.math.Vector3f(-1, 0, 0);
            com.jme3.math.Vector3f suspensionDirection = new com.jme3.math.Vector3f(0, -1, 0);

            vehicle.addWheel(new com.jme3.math.Vector3f(-xOffset, yWheelPos, frontAxleZ), suspensionDirection, axleDirection, restLength, radius, front);
            vehicle.addWheel(new com.jme3.math.Vector3f(xOffset, yWheelPos, frontAxleZ), suspensionDirection, axleDirection, restLength, radius, front);
            vehicle.addWheel(new com.jme3.math.Vector3f(-xOffset, yWheelPos, rearAxleZ), suspensionDirection, axleDirection, restLength, radius, rear);
            vehicle.addWheel(new com.jme3.math.Vector3f(xOffset, yWheelPos, rearAxleZ), suspensionDirection, axleDirection, restLength, radius, rear);

            vehicle.setPhysicsLocation(new com.jme3.math.Vector3f((float) getX(), (float) getY(), (float) getZ()));

            world.getPhysicsThread().enqueue(space -> {
                space.add(vehicle);
                this.vehicle = vehicle;
            });

            for (int i = 0; i < vehicle.getNumWheels(); i++) {
                var e = new ItemDisplayElement(Items.DIAMOND_BLOCK);
                e.setTranslation(new Vector3f(0,-0.25f,0));
                e.setTeleportDuration(3);
                e.setInterpolationDuration(2);
                e.setScale(new Vector3f(radius + 0.2f));
                this.holder.addElement(e);
                this.wheels.add(e);
            }

            this.loader = new DynamicElement(vehicle, s -> {
            }, s -> {
            });
            world.addElement(loader);
        }
    }

    @Override
    public void modifyRawEntityAttributeData(List<ClientboundUpdateAttributesPacket.AttributeSnapshot> data, ServerPlayer player, boolean initial) {
        //data.add(new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.SCALE, 0.2f, List.of()));
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
    public void startSeenByPlayer(ServerPlayer serverPlayer) {
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
    public InteractionResult interactAt(Player player, Vec3 vec3, InteractionHand interactionHand) {
        player.startRiding(this);
        return super.interact(player, interactionHand);
    }

    com.jme3.math.Vector3f pos = new com.jme3.math.Vector3f();
    Quaternion rot = new Quaternion();

    float steering = 0;

    @Override
    public void baseTick() {
        super.baseTick();

        if (this.vehicle != null) {
            this.vehicle.activate();
            this.vehicle.updateWheels();

            if (this.isVehicle() && this.getFirstPassenger() instanceof ServerPlayer player) {
                var input = this.getInput(player);
                this.handleInput(player, input);
            } else {
                this.vehicle.setEnableSleep(true);
                this.vehicle.brake(1000);
            }

            for (int i = 0; i < this.vehicle.getNumWheels(); i++) {
                var wheel = this.vehicle.getWheel(i);
                var carRot = wheel.getWheelWorldRotation(null);
                var axle = wheel.getWheelWorldLocation(null);
                this.wheels.get(i).setOverridePos(new Vec3(axle.x, axle.y, axle.z));
                this.wheels.get(i).setLeftRotation(new Quaternionf(carRot.getX(), carRot.getY(), carRot.getZ(), carRot.getW()));
                this.wheels.get(i).startInterpolationIfDirty();
            }

            var carPos = this.vehicle.getPhysicsLocation(null);
            var carRot = this.vehicle.getPhysicsRotation(null);
            chassis.setOverridePos(new Vec3(carPos.x, carPos.y, carPos.z));
            chassis.setLeftRotation(new Quaternionf(carRot.getX(), carRot.getY(), carRot.getZ(), carRot.getW()));
            chassis.startInterpolationIfDirty();

            var pos = this.vehicle.getPhysicsLocation(null);
            this.setPos(pos.x, pos.y, pos.z);

            List<Packet<? super @NotNull ClientGamePacketListener>> packets = new ArrayList<>();
            var pos1 =(new ClientboundEntityPositionSyncPacket(this.getId(), new PositionMoveRotation(position(), position(), 0f, 0f), false));
            packets.add(pos1);
            for (VirtualElement element : this.holder.getElements()) {
                if (element instanceof GenericEntityElement entityElement) {
                    var id = entityElement.getEntityId();
                    packets.add(new ClientboundEntityPositionSyncPacket(id, new PositionMoveRotation(element.getCurrentPos(), element.getCurrentPos(), 0f, 0f), false));
                }
            }
            this.holder.sendPacket(new ClientboundBundlePacket(packets));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {

    }

    protected void handleInput(ServerPlayer player, Vec3 input) {
        this.steering = Mth.clamp(Mth.lerp(0.2f, steering, (float) input.x * 30f), -45, 45);
        this.vehicle.steer(steering * Mth.DEG_TO_RAD);

        if (player.getLastClientInput().jump()) {
            this.vehicle.accelerate(0);
            for (int i = 0; i < this.vehicle.getNumWheels(); i++) {
                var wheel = this.vehicle.getWheel(i);
                var axle = wheel.getWheelWorldLocation(null);

                var state = level().getBlockState(BlockPos.containing(axle.x, axle.y - 1.25, axle.z));
                if (state.isSolid()) {
                    var rad = 0.25f;
                    ((ServerLevel) level()).sendParticles(new DustParticleOptions(0xFFFFFFFF, 1), axle.x, axle.y, axle.z, 10, rad, rad, rad, 0.11);
                }
            }
        } else {
            this.vehicle.accelerate((float) input.z * 80f);
        }

        this.setBoosting(player.getLastClientInput().sprint());
        if (isBoosting) {
            var axle = this.vehicle.getPhysicsLocation(null);

            var rad = 0.25f;
            ((ServerLevel) level()).sendParticles(ParticleTypes.COPPER_FIRE_FLAME, axle.x, axle.y, axle.z, 10, rad, rad, rad, 0.11);

        }

        this.applyBoost();
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
        return EntityType.SLIME;
    }

    @NotNull
    protected Vec3 getInput(@NotNull Player player) {
        ServerPlayer p = (ServerPlayer) player;
        float x = p.getLastClientInput().left() ? 1 : p.getLastClientInput().right() ? -1 : 0;
        float z = p.getLastClientInput().forward() ? 1 : p.getLastClientInput().backward() ? -1 : 0;
        if (z <= 0.0F) {
            z *= 0.65F;
        }

        return new Vec3(x, 0.0, z);
    }

    public void reset() {
        this.world.getPhysicsThread().enqueue(space -> {
            vehicle.setPhysicsRotation(Quaternion.IDENTITY);
            vehicle.setPhysicsRotation(Quaternion.IDENTITY);
        });
    }
}

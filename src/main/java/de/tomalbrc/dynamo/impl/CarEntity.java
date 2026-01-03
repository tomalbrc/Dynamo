package de.tomalbrc.dynamo.impl;

import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.objects.PhysicsVehicle;
import com.jme3.math.Quaternion;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;

public class CarEntity extends LivingEntity implements PolymerEntity {
    public static Identifier ID = Util.id("car");

    ElementHolder holder;
    ItemDisplayElement chassis;
    List<ItemDisplayElement> wheels = new ArrayList<>();

    DynamicWorld world;
    PhysicsVehicle vehicle;
    DynamicElement loader;

    public CarEntity(EntityType<? extends @NotNull LivingEntity> entityType, Level level) {
        super(entityType, level);

        this.setNoGravity(true);
        this.noPhysics = true;

        chassis = new ItemDisplayElement(Items.DIAMOND_BLOCK);
        chassis.setTeleportDuration(3);
        chassis.setInterpolationDuration(3);
        chassis.setScale(new Vector3f(1.25f, 0.5f, 2.5f));

        this.holder = new ElementHolder();
        this.holder.addElement(chassis);
        EntityAttachment.ofTicking(holder, this);
    }

    @Override
    public void onRemoval(@NotNull RemovalReason removalReason) {
        super.onRemoval(removalReason);

        if (this.world != null)
            this.world.getPhysicsThread().enqueue(space -> space.remove(this.vehicle));
    }

    public void setWorld(DynamicWorld world) {
        this.world = world;
        if (this.vehicle == null) {
            // --- 1. DEFINE THE CHASSIS SHAPE ---
            float halfWidth = 1.25f;
            float halfHeight = 0.5f;
            float halfLength = 1.0f;
            CollisionShape boxShape = new BoxCollisionShape(new com.jme3.math.Vector3f(halfWidth, halfHeight, halfLength));
            float centerOfMass = 1.1f;

            CompoundCollisionShape chassisShape = new CompoundCollisionShape();
            com.jme3.math.Vector3f boxLocalPos = new com.jme3.math.Vector3f(0, centerOfMass, 0);
            chassisShape.addChildShape(boxShape, boxLocalPos);

            float mass = 1000f;
            PhysicsVehicle vehicle = new PhysicsVehicle(chassisShape, mass);
            vehicle.setSuspensionStiffness(100.0f);
            vehicle.setSuspensionCompression(200.0f);
            vehicle.setSuspensionDamping(50.0f);
            vehicle.setFrictionSlip(5.0f);

            vehicle.setAngularDamping(0);
            vehicle.setAngularFactor(1f);

            float yWheelPos = centerOfMass; // relative to com

            boolean front = true;
            boolean rear = false;
            float xOffset = halfWidth * 1.0f;
            float frontAxleZ = halfLength * 0.8f;
            float rearAxleZ = -halfLength * 1.2f;
            float radius = 0.6f;
            float restLength = 2.0f;

            com.jme3.math.Vector3f axleDirection = new com.jme3.math.Vector3f(-1, 0, 0);
            com.jme3.math.Vector3f suspensionDirection = new com.jme3.math.Vector3f(0, -1, 0);

            vehicle.addWheel(new com.jme3.math.Vector3f(-xOffset, yWheelPos, frontAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, front);
            vehicle.addWheel(new com.jme3.math.Vector3f(xOffset, yWheelPos, frontAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, front);
            vehicle.addWheel(new com.jme3.math.Vector3f(-xOffset, yWheelPos, rearAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, rear);
            vehicle.addWheel(new com.jme3.math.Vector3f(xOffset, yWheelPos, rearAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, rear);

            vehicle.setPhysicsLocation(new com.jme3.math.Vector3f((float) getX(), (float) getY(), (float) getZ()));

            world.getPhysicsThread().enqueue(space -> {
                space.add(vehicle);
                this.vehicle = vehicle;
            });

            for (int i = 0; i < vehicle.getNumWheels(); i++) {
                var e = new ItemDisplayElement(Items.DIAMOND_BLOCK);
                e.setTeleportDuration(3);
                e.setInterpolationDuration(3);
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
                var input = getInput(player);
                this.vehicle.accelerate((float) input.z * 1000f);

                this.steering = Mth.clamp(Mth.lerp(0.4f, steering, (float) input.x*40f), -90, 90);
                this.vehicle.steer(steering * Mth.DEG_TO_RAD);

                if (player.getLastClientInput().jump()) {
                    this.vehicle.brake(0.5f);
                    for (int i = 0; i < this.vehicle.getNumWheels(); i++) {
                        var wheel = this.vehicle.getWheel(i);
                        var axle = wheel.getWheelWorldLocation(null);

                        var state = level().getBlockState(BlockPos.containing(axle.x, axle.y - 0.1, axle.z));
                        if (state.isSolid()) {
                            ((ServerLevel) level()).sendParticles(new DustParticleOptions(0, 1), axle.x, axle.y, axle.z, 10, 0.1f, 0.1f, 0.1f, 0.11);
                        }
                    }
                }
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
        }
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
        return EntityType.ARMOR_STAND;
    }

    @NotNull
    protected Vec3 getInput(@NotNull Player player) {
        ServerPlayer p = (ServerPlayer) player;
        float x = p.getLastClientInput().left() ? 1 : p.getLastClientInput().right() ? -1 : 0;
        float z = p.getLastClientInput().forward() ? 1 : p.getLastClientInput().backward() ? -1 : 0;
        if (z <= 0.0F) {
            z *= 0.5F;
        }

        return new Vec3(x, 0.0, z);
    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.LEFT;
    }

    public void reset() {
        this.world.getPhysicsThread().enqueue(space -> vehicle.setPhysicsRotation(Quaternion.IDENTITY));
    }
}

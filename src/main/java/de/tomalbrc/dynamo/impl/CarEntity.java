package de.tomalbrc.dynamo.impl;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.jme3.bullet.objects.PhysicsVehicle;
import de.tomalbrc.dynamo.impl.physics.DynamicElement;
import de.tomalbrc.dynamo.impl.world.DynamicWorld;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.resources.Identifier;
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
import java.util.Collection;
import java.util.List;

public class CarEntity extends LivingEntity implements PolymerEntity {
    public static Identifier ID = Util.id("car");

    ElementHolder holder;
    ItemDisplayElement e;
    List<ItemDisplayElement> wheels = new ArrayList<>();

    DynamicWorld world;
    PhysicsVehicle vehicle;
    DynamicElement loader;

    public CarEntity(EntityType<? extends @NotNull LivingEntity> entityType, Level level) {
        super(entityType, level);

        this.setNoGravity(true);
        this.noPhysics = true;

        e = new ItemDisplayElement(Items.DIAMOND_BLOCK);
        e.setTeleportDuration(2);
        e.setInterpolationDuration(2);
        e.setScale(new Vector3f(2.5f, 1.7f, 2.8f));

        this.holder = new ElementHolder();
        this.holder.addElement(e);
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
            /*
             * 1. THE SHAPE
             * Switched to BoxCollisionShape.
             * Dimensions are Half-Extents (distance from center to edge).
             * 1.25m wide (2.5m total), 0.5m high (1m total), 2.5m long (5m total).
             */
            float halfWidth = 1.25f;
            float halfHeight = 0.5f;
            float halfLength = 2.5f;

            // Ensure you have the correct import for BoxCollisionShape
            CollisionShape boxShape = new BoxCollisionShape(
                    new com.jme3.math.Vector3f(halfWidth, halfHeight, halfLength)
            );

            /*
             * 2. THE PHYSICS BODY
             * Increased mass to 800kg for better simulation stability.
             */
            float mass = 800f;
            PhysicsVehicle vehicle = new PhysicsVehicle(boxShape, mass);

            /*
             * 3. SUSPENSION TUNING FOR VOXELS
             * Voxel terrain is harsh. We need soft springs with HIGH damping.
             */
            // Stiffness: ~20.0 allows the wheel to move up when hitting a block
            // without launching the whole car into the air immediately.
            vehicle.setSuspensionStiffness(20.0f);

            // Compression: Resists the wheel moving up.
            // Keep this moderate so the wheel CAN move up over a block.
            vehicle.setSuspensionCompression(3.0f);

            // Damping: Resists the wheel moving down (rebound).
            // HIGH value here is critical to stop the car from bouncing
            // after dropping down a block height.
            vehicle.setSuspensionDamping(4.0f);

            // Friction: Grip. Too high on blocks = climbing walls.
            vehicle.setFrictionSlip(2.5f);

            // Optional: If your API supports it, set MaxSuspensionTravelCm to 100f (1 block)
            // vehicle.setMaxSuspensionTravelCm(100f);

            /*
             * 4. WHEEL CONFIGURATION
             */
            boolean front = true;
            boolean rear = false;

            // Move wheels slightly outside the box width for stability (Wide stance)
            float xOffset = halfWidth * 1.1f;

            // Move axles towards the ends of the chassis
            float frontAxleZ = halfLength * 0.8f;
            float rearAxleZ = -halfLength * 0.8f;

            // Radius: 0.75f (1.5 blocks high) is much safer than 2.5f.
            float radius = 0.75f;

            // Rest Length: Needs to be long enough to handle a 1-block drop.
            float restLength = 1.2f;

            com.jme3.math.Vector3f axleDirection = new com.jme3.math.Vector3f(-1f, 0f, 0f);
            com.jme3.math.Vector3f suspensionDirection = new com.jme3.math.Vector3f(0f, -1f, 0f);

            // Add 4 wheels
            vehicle.addWheel(new com.jme3.math.Vector3f(-xOffset, 0f, frontAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, front);
            vehicle.addWheel(new com.jme3.math.Vector3f(xOffset, 0f, frontAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, front);
            vehicle.addWheel(new com.jme3.math.Vector3f(-xOffset, 0f, rearAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, rear);
            vehicle.addWheel(new com.jme3.math.Vector3f(xOffset, 0f, rearAxleZ),
                    suspensionDirection, axleDirection, restLength, radius, rear);

            this.vehicle = vehicle;
            this.vehicle.setPhysicsLocation(new com.jme3.math.Vector3f((float)getX(), (float)getY(), (float)getZ()));
            world.getPhysicsThread().enqueue(space -> space.add(this.vehicle));

            // Visuals
            for (int i = 0; i < this.vehicle.getNumWheels(); i++) {
                var e = new ItemDisplayElement(Items.DIAMOND_BLOCK);
                e.setTeleportDuration(2);
                e.setInterpolationDuration(2);
                // Scale visual to match physics radius roughly (optional tweak)
                e.setScale(new Vector3f(1.0f));
                this.holder.addElement(e);
                this.wheels.add(e);
            }

            this.loader = new DynamicElement(vehicle, s -> {}, s -> {});
            world.addElement(loader);
        }
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec3, InteractionHand interactionHand) {
        player.startRiding(this);
        return super.interact(player, interactionHand);
    }

    @Override
    public void baseTick() {
        super.baseTick();

        if (this.vehicle != null) {
            this.vehicle.activate();
            this.vehicle.updateWheels();

            if (this.isVehicle() && this.getFirstPassenger() instanceof ServerPlayer player) {
                var input = getInput(player);
                this.vehicle.accelerate((float) input.z*500f);
                this.vehicle.steer((float) input.x*45f);
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
            e.setOverridePos(new Vec3(carPos.x, carPos.y, carPos.z));
            e.setLeftRotation(new Quaternionf(carRot.getX(), carRot.getY(), carRot.getZ(), carRot.getW()));
            e.startInterpolationIfDirty();

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
}

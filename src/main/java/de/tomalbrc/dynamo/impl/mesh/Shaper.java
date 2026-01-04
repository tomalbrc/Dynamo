package de.tomalbrc.dynamo.impl.mesh;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.EmptyShape;
import com.jme3.math.Vector3f;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Shaper {
    public static final VoxelShape ITEM = Shapes.create(AABB.ofSize(Vec3.ZERO, 1, 1, 0.1));

    private Shaper(){}

    public static CollisionShape shape(VoxelShape shape) {
        if (shape.isEmpty())
            return new EmptyShape(false);

        var compound = new CompoundCollisionShape();
        shape.forAllBoxes((minX, minYBox, minZ, maxX, maxYBox, maxZ) -> {
            float worldMinX = (float) (minX);
            float worldMinY = (float) (minYBox);
            float worldMinZ = (float) (minZ);
            float worldMaxX = (float) (maxX);
            float worldMaxY = (float) (maxYBox);
            float worldMaxZ = (float) (maxZ);

            float hx = (worldMaxX - worldMinX) * 0.5f;
            float hy = (worldMaxY - worldMinY) * 0.5f;
            float hz = (worldMaxZ - worldMinZ) * 0.5f;

            float cx = worldMinX + hx;
            float cy = worldMinY + hy;
            float cz = worldMinZ + hz;

            if (hx <= 1e-6f || hy <= 1e-6f || hz <= 1e-6f) return;

            BoxCollisionShape box = new BoxCollisionShape(new Vector3f(hx, hy, hz));
            compound.addChildShape(box, new Vector3f(cx, cy, cz));
        });

        return compound;
    }
}

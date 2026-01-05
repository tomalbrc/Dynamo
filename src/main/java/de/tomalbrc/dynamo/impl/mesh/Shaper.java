package de.tomalbrc.dynamo.impl.mesh;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Shaper {
    public static final VoxelShape ITEM = Shapes.create(AABB.ofSize(Vec3.ZERO, 1, 1, 0.1));

    private Shaper(){}

    public static StaticCompoundShapeSettings shape(VoxelShape shape) {
        var compound = new StaticCompoundShapeSettings();
        if (shape.isEmpty())
            return compound;

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

            BoxShapeSettings box = new BoxShapeSettings(hx, hy, hz);
            compound.addShape( new com.github.stephengold.joltjni.Vec3(cx, cy, cz), new Quat(), box);
        });

        return compound;
    }
}

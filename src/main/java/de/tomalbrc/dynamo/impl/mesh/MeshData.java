package de.tomalbrc.dynamo.impl.mesh;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class MeshData {
    public final FloatBuffer positions;
    public final IntBuffer indices;

    public MeshData(FloatBuffer positions, IntBuffer indices) {
        this.positions = positions;
        this.indices = indices;
    }
}

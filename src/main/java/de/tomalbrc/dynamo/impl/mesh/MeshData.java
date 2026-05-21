package de.tomalbrc.dynamo.impl.mesh;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class MeshData {
    public FloatArrayList positions = new FloatArrayList();
    public IntArrayList indices = new IntArrayList();

    public MeshData(FloatArrayList floats, IntArrayList integers) {
        this.positions = floats;
        this.indices = integers;
    }

    public MeshData() {

    }
}
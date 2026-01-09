package de.tomalbrc.dynamo.impl.util;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public final class StlExporter {
    public static void writeAsciiStl(
            String filename,
            String solidName,
            FloatArrayList vertices,
            IntArrayList indices,
            float offsetX, float offsetY, float offsetZ,
            boolean doubleSided
    ) throws IOException {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {

            writer.write("solid " + solidName);
            writer.newLine();

            for (int t = 0; t < indices.size(); t += 3) {
                int i0 = indices.getInt(t);
                int i1 = indices.getInt(t + 1);
                int i2 = indices.getInt(t + 2);

                float x0 = vertices.getFloat(i0 * 3);
                float y0 = vertices.getFloat(i0 * 3 + 1);
                float z0 = vertices.getFloat(i0 * 3 + 2);

                float x1 = vertices.getFloat(i1 * 3);
                float y1 = vertices.getFloat(i1 * 3 + 1);
                float z1 = vertices.getFloat(i1 * 3 + 2);

                float x2 = vertices.getFloat(i2 * 3);
                float y2 = vertices.getFloat(i2 * 3 + 1);
                float z2 = vertices.getFloat(i2 * 3 + 2);

                float ux = x1 - x0, uy = y1 - y0, uz = z1 - z0;
                float vx = x2 - x0, vy = y2 - y0, vz = z2 - z0;

                float nx = uy * vz - uz * vy;
                float ny = uz * vx - ux * vz;
                float nz = ux * vy - uy * vx;

                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len != 0f) {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                }

                x0 += offsetX;
                y0 += offsetY;
                z0 += offsetZ;
                x1 += offsetX;
                y1 += offsetY;
                z1 += offsetZ;
                x2 += offsetX;
                y2 += offsetY;
                z2 += offsetZ;

                writer.write("  facet normal " + nx + " " + ny + " " + nz);
                writer.newLine();
                writer.write("    outer loop");
                writer.newLine();
                writer.write("      vertex " + x0 + " " + y0 + " " + z0);
                writer.newLine();
                writer.write("      vertex " + x1 + " " + y1 + " " + z1);
                writer.newLine();
                writer.write("      vertex " + x2 + " " + y2 + " " + z2);
                writer.newLine();
                writer.write("    endloop");
                writer.newLine();
                writer.write("  endfacet");
                writer.newLine();

                if (doubleSided) {
                    // backside for double sided tris
                    writer.write("  facet normal " + (-nx) + " " + (-ny) + " " + (-nz));
                    writer.newLine();
                    writer.write("    outer loop");
                    writer.newLine();
                    writer.write("      vertex " + x0 + " " + y0 + " " + z0);
                    writer.newLine();
                    writer.write("      vertex " + x2 + " " + y2 + " " + z2);
                    writer.newLine();
                    writer.write("      vertex " + x1 + " " + y1 + " " + z1);
                    writer.newLine();
                    writer.write("    endloop");
                    writer.newLine();
                    writer.write("  endfacet");
                    writer.newLine();
                }
            }

            writer.write("endsolid " + solidName);
            writer.newLine();
        }
    }
}

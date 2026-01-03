package de.tomalbrc.dynamo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public final class StlExporter {

    public static void writeAsciiStl(
            String filename,
            String solidName,
            List<Float> vertices,
            List<Integer> indices
    ) throws IOException {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {

            writer.write("solid " + solidName);
            writer.newLine();

            for (int t = 0; t < indices.size(); t += 3) {
                int i0 = indices.get(t);
                int i1 = indices.get(t + 1);
                int i2 = indices.get(t + 2);

                float x0 = vertices.get(i0 * 3);
                float y0 = vertices.get(i0 * 3 + 1);
                float z0 = vertices.get(i0 * 3 + 2);

                float x1 = vertices.get(i1 * 3);
                float y1 = vertices.get(i1 * 3 + 1);
                float z1 = vertices.get(i1 * 3 + 2);

                float x2 = vertices.get(i2 * 3);
                float y2 = vertices.get(i2 * 3 + 1);
                float z2 = vertices.get(i2 * 3 + 2);

                float ux = x1 - x0, uy = y1 - y0, uz = z1 - z0;
                float vx = x2 - x0, vy = y2 - y0, vz = z2 - z0;

                float nx = uy * vz - uz * vy;
                float ny = uz * vx - ux * vz;
                float nz = ux * vy - uy * vx;

                float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
                if (len != 0f) {
                    nx /= len; ny /= len; nz /= len;
                }

                // Original side
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

                // Reverse side
                // Invert normal and triangle winding
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

            writer.write("endsolid " + solidName);
            writer.newLine();
        }
    }
}

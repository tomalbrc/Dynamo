package de.tomalbrc.dynamo;

import de.tomalbrc.dynamo.impl.mesh.ChunkMeshGenerator;
import de.tomalbrc.dynamo.impl.util.StlExporter;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@BenchmarkMode(Mode.AverageTime)      // Measure average execution time
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)                  // One instance per thread
public class MeshBenchmark {

    @Benchmark
    public void testSmoothGen() {
        boolean[][][] array = new boolean[20][20][20];
        Random random = new Random(1);

        for (int x = 0; x < array.length; x++) {
            for (int y = 0; y < array[x].length; y++) {
                for (int z = 0; z < array[x][y].length; z++) {
                    array[x][y][z] = random.nextBoolean();
                }
            }
        }

        var mesh = ChunkMeshGenerator.generateSmoothedMesh(array,0,0,0, 0.5f);

        assertNotNull(mesh);
        assertNotNull(mesh.positions);

        try {
            StlExporter.writeAsciiStl("/tmp/test-section.stl", "section", mesh.positions, mesh.indices, mesh.indices.capacity(), 0, 0, 0, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public void testBlockGen() {
        boolean[][][] array = new boolean[20][20][20];
        Random random = new Random();

        for (int x = 0; x < array.length; x++) {
            for (int y = 0; y < array[x].length; y++) {
                for (int z = 0; z < array[x][y].length; z++) {
                    array[x][y][z] = random.nextBoolean();
                }
            }
        }

        var mesh = ChunkMeshGenerator.generateMesh(array,0,0,0);

        assertNotNull(mesh);
        assertNotNull(mesh.positions);
    }
}
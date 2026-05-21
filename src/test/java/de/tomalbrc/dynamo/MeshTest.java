package de.tomalbrc.dynamo;

import com.github.noconnor.junitperf.JUnitPerfInterceptor;
import com.github.noconnor.junitperf.JUnitPerfTest;
import de.tomalbrc.dynamo.impl.mesh.ChunkMeshGenerator;
import de.tomalbrc.dynamo.impl.util.StlExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(JUnitPerfInterceptor.class)
class MeshTest {
    @Test
    @JUnitPerfTest(
            threads = 1,
            durationMs = 1_000,
            maxExecutionsPerSecond = 10_000
    )
    void testSmoothGen() {
        if (true)return;

        boolean[][][] array = new boolean[20][20][20];
        Random random = new Random();

        var noise = new FastNoiseLite(1);

        for (int x = 0; x < array.length; x++) {
            for (int y = 0; y < array[x].length; y++) {
                for (int z = 0; z < array[x][y].length; z++) {
                    array[x][y][z] = noise.GetNoise(x / 10f, y / 10f, z / 10f) > 0.5;
                }
            }
        }

        var mesh = ChunkMeshGenerator.generateSmoothedMesh(array, 0.5f);

        assertNotNull(mesh);
        assertNotNull(mesh.positions);

        try {
            StlExporter.writeAsciiStl("/tmp/test-section.stl", "section", mesh.positions, mesh.indices, 0, 0, 0, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSmoothGen2() {
        if (true)return;

        boolean[][][] array = new boolean[20][20][20];

        var noise = new FastNoiseLite(1);
        float s = 2.f;

        for (int x = 0; x < array.length; x++) {
            for (int y = 0; y < array[x].length; y++) {
                for (int z = 0; z < array[x][y].length; z++) {
                    array[x][y][z] = noise.GetNoise(x * s, y * s, z * s) > 0;
                }
            }
        }

        var mesh = ChunkMeshGenerator.generateSmoothedMesh(array, 0.5f);

        assertNotNull(mesh);
        assertNotNull(mesh.positions);

        try {
            StlExporter.writeAsciiStl("/tmp/test-section.stl", "section", mesh.positions, mesh.indices, 0, 0, 0, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testBlockGen() {
        if (true)return;

        boolean[][][] array = new boolean[20][20][20];
        Random random = new Random();

        for (int x = 0; x < array.length; x++) {
            for (int y = 0; y < array[x].length; y++) {
                for (int z = 0; z < array[x][y].length; z++) {
                    array[x][y][z] = random.nextBoolean();
                }
            }
        }

        var mesh = ChunkMeshGenerator.generateMesh(array);

        assertNotNull(mesh);
        assertNotNull(mesh.positions);
    }
}
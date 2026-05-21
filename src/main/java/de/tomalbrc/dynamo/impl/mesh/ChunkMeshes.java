package de.tomalbrc.dynamo.impl.mesh;

import de.tomalbrc.dynamo.Dynamo;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.level.ChunkPos;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ChunkMeshes {
    int version = 1;

    private final ChunkPos chunkPos;
    private final Map<Long, MeshData> meshes;

    public ChunkMeshes(ChunkPos chunkPos) {
        this.chunkPos = chunkPos;
        this.meshes = new HashMap<>();
    }

    public ChunkMeshes(ChunkPos chunkPos, Map<Long, MeshData> meshes) {
        this.chunkPos = chunkPos;
        this.meshes = meshes;
    }

    public void put(MeshPos pos, MeshData meshData) {
        this.meshes.put(pos.asLong(), meshData);
    }

    public MeshData get(MeshPos pos) {
        return this.meshes.get(pos.asLong());
    }

    public static ChunkMeshes load(Path path) {
        if (Files.exists(path)) {
            try (var s = new FileInputStream(path.toFile())) {
                return load(s.readAllBytes());
            } catch (IOException e) {
                Dynamo.LOGGER.error("Could not load mesh at {} from file", path, e);
            }
        }

        return null;
    }

    public static ChunkMeshes load(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(decompressGzip(bytes)))) {
            int version = in.readInt();
            int chunkX = in.readInt();
            int chunkZ = in.readInt();
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

            int count = in.readInt();
            Map<Long, MeshData> meshes = new HashMap<>(count);

            for (int i = 0; i < count; i++) {
                long key = in.readLong();

                int posLen = in.readInt();
                float[] posArr = new float[posLen];
                for (int p = 0; p < posLen; p++) {
                    posArr[p] = in.readFloat();
                }

                int idxLen = in.readInt();
                int[] idxArr = new int[idxLen];
                for (int j = 0; j < idxLen; j++) {
                    idxArr[j] = in.readInt();
                }

                meshes.put(key, new MeshData(new FloatArrayList(posArr), new IntArrayList(idxArr)));
            }

            ChunkMeshes cm = new ChunkMeshes(chunkPos, meshes);
            cm.version = version;
            return cm;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ChunkMeshes", e);
        }
    }

    public byte[] save() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {

            out.writeInt(version);
            out.writeInt(chunkPos.x());
            out.writeInt(chunkPos.z());

            out.writeInt(meshes.size());
            for (Map.Entry<Long, MeshData> entry : meshes.entrySet()) {
                out.writeLong(entry.getKey());

                MeshData mesh = entry.getValue();
                var posBuf = mesh.positions;
                int posLen = posBuf.size();
                out.writeInt(posLen);
                for (int p = 0; p < posLen; p++) {
                    out.writeFloat(posBuf.get(p));
                }

                var idxBuf = mesh.indices;
                int idxLen = idxBuf.size();
                out.writeInt(idxLen);
                for (int i = 0; i < idxLen; i++) {
                    out.writeInt(idxBuf.get(i));
                }
            }

            return compressGzip(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save ChunkMeshes", e);
        }
    }

    public static byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    public static byte[] decompressGzip(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }


    public void remove(MeshPos meshPos) {
        meshes.remove(meshPos.asLong());
    }
}

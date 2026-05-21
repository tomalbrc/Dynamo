package de.tomalbrc.dynamo.impl.config;

import com.google.gson.Gson;
import de.tomalbrc.dynamo.Dynamo;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class ModConfig {
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("dynamo");
    private static final Path CONFIG_FILE_PATH = CONFIG_DIR.resolve(Dynamo.MODID + ".json");
    private static ModConfig instance;
    private static final Gson gson = DynamoJson.createGson();

    // entries

    public int chunkSize = 16;
    public boolean smoothMesh = true;
    public boolean exportMesh = false;
    public PhysicsConfig physics = new PhysicsConfig();

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (!CONFIG_FILE_PATH.toFile().exists()) {
            instance = new ModConfig();

            try {
                if (CONFIG_FILE_PATH.toFile().createNewFile()) {
                    FileOutputStream stream = new FileOutputStream(CONFIG_FILE_PATH.toFile());
                    stream.write(gson.toJson(instance).getBytes(StandardCharsets.UTF_8));
                    stream.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                ModConfig.instance = gson.fromJson(new FileReader(ModConfig.CONFIG_FILE_PATH.toFile()), ModConfig.class);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
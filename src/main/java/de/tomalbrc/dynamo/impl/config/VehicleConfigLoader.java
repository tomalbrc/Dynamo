package de.tomalbrc.dynamo.impl.config;

import com.google.gson.Gson;
import de.tomalbrc.dynamo.impl.config.vehicle.VehicleConfig;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class VehicleConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(VehicleConfigLoader.class);
    private static final Path VEHICLES_DIR = ModConfig.CONFIG_DIR.resolve("vehicles");
    private static final Gson GSON = DynamoJson.createGson();

    private static final Map<Identifier, VehicleConfig> CONFIGS = new HashMap<>();

    public static void loadAll() {
        CONFIGS.clear();
        try {
            Files.createDirectories(VEHICLES_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create vehicles directory", e);
        }

        File[] jsonFiles = VEHICLES_DIR.toFile().listFiles((_, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            createDefaultConfig();

            jsonFiles = VEHICLES_DIR.toFile().listFiles((_, name) -> name.endsWith(".json"));
        }

        if (jsonFiles != null) {
            for (File file : jsonFiles) {
                try (FileReader reader = new FileReader(file)) {
                    VehicleConfig config = GSON.fromJson(reader, VehicleConfig.class);
                    String name = file.getName().replace(".json", "");
                    CONFIGS.put(config.id, config);
                    LOGGER.info("Loaded vehicle config: {}", name);
                } catch (Exception e) {
                    LOGGER.error("Failed to load vehicle config from {}: {}", file.getName(), e.getMessage());
                }
            }
        }
    }

    private static void createDefaultConfig() {
        VehicleConfig defaultConfig = new VehicleConfig();

        Path defaultFile = VEHICLES_DIR.resolve("default.json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(defaultFile.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(defaultConfig, writer);
            LOGGER.info("Created default vehicle config at {}", defaultFile);
        } catch (IOException e) {
            LOGGER.error("Failed to create default vehicle config", e);
        }
    }

    public static VehicleConfig get(Identifier name) {
        return CONFIGS.get(name);
    }

    public static Map<Identifier, VehicleConfig> getAll() {
        return CONFIGS;
    }
}
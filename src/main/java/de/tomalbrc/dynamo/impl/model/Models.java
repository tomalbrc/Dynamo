package de.tomalbrc.dynamo.impl.model;

import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.AjBlueprintLoader;
import de.tomalbrc.bil.file.loader.AjModelLoader;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import de.tomalbrc.dynamo.Dynamo;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class Models {
    private static final Map<String, Model> MODELS = new HashMap<>();

    public static void copyDefaultModel(String modelPath, Path targetPath) {
        if (Files.exists(targetPath)) return;

        try (InputStream input = Models.class.getResourceAsStream(modelPath)) {
            if (input == null) {
                throw new IllegalArgumentException("Model not found: " + modelPath);
            }
            Files.createDirectories(targetPath.getParent());
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void load() {
        var folder = FabricLoader.getInstance().getConfigDir().resolve("dynamo/models/");
        if (!Files.exists(folder)) {
            copyDefaultModel("/model/dynamo/car.bbmodel", folder.resolve("car.bbmodel"));

            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                Dynamo.LOGGER.error("Could not create config dir!");
            }
        }


        try {
            var files = Files.walk(folder);
            files.forEach(x -> {
                var basename = FilenameUtils.getBaseName(x.toString());
                var ext = FilenameUtils.getExtension(x.toString());

                if (ext.equals("bbmodel")) MODELS.put(basename, BbModelLoader.load(x.toString()));
                if (ext.equals("ajmodel")) MODELS.put(basename, AjModelLoader.load(x.toString()));
                if (ext.equals("ajblueprint")) MODELS.put(basename, AjBlueprintLoader.load(x.toString()));
            });
            files.close();
        } catch (IOException e) {
            Dynamo.LOGGER.error("Could not load model!");
        }
    }

    public static Model get(String id) {
        return MODELS.get(id);
    }

    public static void put(String id, Model model) {
        MODELS.put(id, model);
    }
}

package de.tomalbrc.dynamo.impl.model;

import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class Loader {
    public static Optional<Model> load(String model) {
        try {
            Model m = BbModelLoader.load(Identifier.fromNamespaceAndPath(Dynamo.MODID, model));
            return Optional.ofNullable(m);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

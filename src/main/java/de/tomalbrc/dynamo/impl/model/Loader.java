package de.tomalbrc.dynamo.impl.model;

import com.github.stephengold.joltjni.operator.Op;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.AjBlueprintLoader;
import de.tomalbrc.bil.file.loader.AjModelLoader;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class Loader {
    public static Optional<Model> load(String model) {
        try {
            Model m = null;

            m = BbModelLoader.load(Identifier.fromNamespaceAndPath(Dynamo.MODID, model));

            return Optional.ofNullable(m);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

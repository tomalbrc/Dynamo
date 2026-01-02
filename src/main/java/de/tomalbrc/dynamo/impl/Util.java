package de.tomalbrc.dynamo.impl;

import de.tomalbrc.dynamo.Dynamo;
import net.minecraft.resources.Identifier;

public class Util {
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Dynamo.MODID, path);
    }
}
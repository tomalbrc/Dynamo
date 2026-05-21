package de.tomalbrc.dynamo.impl.config;

import com.google.gson.*;
import de.tomalbrc.bil.json.SimpleCodecDeserializer;
import it.unimi.dsi.fastutil.floats.FloatList;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStackTemplate;
import org.joml.Vector3f;

import java.lang.reflect.Type;

public class DynamoJson {
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Vector3f.class, new SimpleCodecDeserializer<>(ExtraCodecs.VECTOR3F))
                .registerTypeAdapter(Identifier.class, new SimpleCodecDeserializer<>(Identifier.CODEC))
                .registerTypeAdapter(ItemStackTemplate.class, new SimpleCodecDeserializer<>(ItemStackTemplate.CODEC))
                .registerTypeAdapter(FloatList.class, new FloatListAdapter())
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
                .setPrettyPrinting()
                .create();
    }

    private static class FloatListAdapter implements JsonSerializer<FloatList>, JsonDeserializer<FloatList> {
        @Override
        public JsonElement serialize(FloatList src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray array = new JsonArray();
            for (float f : src) array.add(f);
            return array;
        }

        @Override
        public FloatList deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonArray array = json.getAsJsonArray();
            float[] arr = new float[array.size()];
            for (int i = 0; i < array.size(); i++) arr[i] = array.get(i).getAsFloat();
            return FloatList.of(arr);
        }
    }
}
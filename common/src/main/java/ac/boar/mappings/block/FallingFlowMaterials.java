package ac.boar.mappings.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.cloudburstmc.nbt.NbtMap;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * The resource comes from Windows Bedrock Dedicated Server runs for the versions in GAME_VERSIONS
 * A custom Endstone probe exported native-typed block states and material flags
 * I had a script check and merge those exports into falling-flow-materials.json.gz (compressed to save ~7.8MB)
 */
public final class FallingFlowMaterials {

    private static final String RESOURCE = "/ac/boar/mappings/block/falling-flow-materials.json.gz";
    private static final String RESOURCE_SHA256 = "54cfd4c1f3b676380439a983dd4e094b3d897de15c66074b761772245be3d181";
    private static final String[] GAME_VERSIONS = {"26.20.5", "26.33.1", "26.40.8", "26.45.1", "26.51.1", "26.3.1", "26.12.2"};
    private static final int[] COUNTS = {16899, 16913, 17499, 17499, 22091, 15845, 15846};

    private FallingFlowMaterials() {
    }

    public static Lookup forProtocol(int protocol) {
        int gameVersionBit = switch (protocol) {
            case 924 -> 32;
            case 944 -> 64;
            case 975 -> 1;
            case 1001 -> 2;
            case 2168 -> 4;
            case 2169 -> 8;
            case 2192, 2193 -> 16;
            default -> 0;
        };
        return new Lookup(gameVersionBit, gameVersionBit == 0 ? Map.of() : Data.STATES);
    }

    public static final class Lookup {
        private final int gameVersionBit;
        private final Map<Identity, Entry> states;

        private Lookup(int gameVersionBit, Map<Identity, Entry> states) {
            this.gameVersionBit = gameVersionBit;
            this.states = states;
        }

        public boolean isSupported() {
            return gameVersionBit != 0;
        }

        public FallingFlowMaterial resolve(String name, NbtMap values) {
            if (!isSupported()) {
                return FallingFlowMaterial.UNSUPPORTED_PROTOCOL;
            }
            if (name == null || name.isEmpty() || values == null) {
                return FallingFlowMaterial.MALFORMED;
            }
            for (Object value : values.values()) {
                if (!(value instanceof Byte || value instanceof Integer || value instanceof String)) {
                    return FallingFlowMaterial.MALFORMED;
                }
            }
            Entry entry = states.get(new Identity(name, values));
            return entry != null && (entry.gameVersionMask() & gameVersionBit) != 0
                    ? entry.material() : FallingFlowMaterial.NOT_SEARCHED;
        }
    }

    private record Identity(String name, NbtMap states) {
    }

    private record Entry(FallingFlowMaterial material, int gameVersionMask) {
    }

    private static final class Data {
        private static final Map<Identity, Entry> STATES = load();
    }

    private static Map<Identity, Entry> load() {
        var resource = FallingFlowMaterials.class.getResourceAsStream(RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("The falling-flow material data is missing.");
        }
        try (resource) {
            byte[] compressed = resource.readAllBytes();
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compressed)).equals(RESOURCE_SHA256));
            try (var input = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(compressed)), StandardCharsets.UTF_8)) {
                var root = new JsonParser().parse(input).getAsJsonObject();
                require(root.entrySet().size() == 5);
                for (String key : Set.of("schema", "inventories", "counts", "materials", "states")) {
                    require(root.has(key));
                }
                require(integer(root.get("schema")) == 1);
                JsonArray gameVersions = array(root.get("inventories"), GAME_VERSIONS.length);
                JsonArray expectedCounts = array(root.get("counts"), COUNTS.length);
                for (int i = 0; i < GAME_VERSIONS.length; i++) {
                    require(string(gameVersions.get(i)).equals(GAME_VERSIONS[i]));
                    require(integer(expectedCounts.get(i)) == COUNTS[i]);
                }
                JsonArray materials = array(root.get("materials"), 25);
                FallingFlowMaterial[] results = new FallingFlowMaterial[materials.size()];
                for (int i = 0; i < materials.size(); i++) {
                    JsonArray material = array(materials.get(i), 7);
                    require(integer(material.get(0)) == i);
                    for (int j = 1; j < 7; j++) {
                        int flag = integer(material.get(j));
                        require(flag == 0 || flag == 1);
                    }
                    results[i] = integer(material.get(5)) == 1
                            ? FallingFlowMaterial.APPLIES_DOWNWARD_FLOW : FallingFlowMaterial.NO_DOWNWARD_FLOW;
                    require(results[i].appliesDownwardFlow() == (integer(material.get(5)) == 1));
                }
                JsonArray rows = array(root.get("states"), 22685);
                Map<Identity, Entry> states = new HashMap<>(rows.size());
                int[] counts = new int[COUNTS.length];
                for (JsonElement element : rows) {
                    JsonArray row = array(element, 4);
                    String name = string(row.get(0));
                    require(name.startsWith("minecraft:") && name.length() > "minecraft:".length());
                    var values = NbtMap.builder();
                    Set<String> keys = new HashSet<>();
                    for (JsonElement stateElement : row.get(1).getAsJsonArray()) {
                        JsonArray state = array(stateElement, 3);
                        String key = string(state.get(0));
                        require(!key.isEmpty() && keys.add(key));
                        switch (string(state.get(1))) {
                            case "b" -> {
                                int value = integer(state.get(2));
                                require(value == 0 || value == 1);
                                values.putByte(key, (byte) value);
                            }
                            case "i" -> values.putInt(key, integer(state.get(2)));
                            case "s" -> values.putString(key, string(state.get(2)));
                            default -> throw new IllegalArgumentException("The material data contains an invalid state type.");
                        }
                    }
                    int material = integer(row.get(2));
                    int mask = integer(row.get(3));
                    require(material >= 0 && material < results.length && mask > 0 && mask < (1 << GAME_VERSIONS.length));
                    require(states.put(new Identity(name, values.build()), new Entry(results[material], mask)) == null);
                    for (int i = 0; i < counts.length; i++) {
                        if ((mask & (1 << i)) != 0) {
                            counts[i]++;
                        }
                    }
                }
                for (int i = 0; i < counts.length; i++) {
                    require(counts[i] == COUNTS[i]);
                }
                return Map.copyOf(states);
            }
        } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
            throw new IllegalStateException("The falling-flow material data is invalid.", exception);
        }
    }

    private static JsonArray array(JsonElement value, int size) {
        JsonArray array = value.getAsJsonArray();
        require(array.size() == size);
        return array;
    }

    private static String string(JsonElement value) {
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString());
        return value.getAsString();
    }

    private static int integer(JsonElement value) {
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber());
        return value.getAsBigDecimal().intValueExact();
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("The material data does not match its schema.");
        }
    }
}

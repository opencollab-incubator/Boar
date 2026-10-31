package ac.boar.anticheat.config;

import ac.boar.anticheat.BoarPlatform;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;

// Credit to https://github.com/onebeastchris/MagicMenu
public class ConfigLoader {
    public static <T> T load(BoarPlatform platform, Class<?> extensionClass, Class<T> configClass, T  defaultConfig) {
        File configFile = platform.dataFolder().resolve("config.yml").toFile();

        // Ensure the data folder exists
        if (!platform.dataFolder().toFile().exists()) {
            if (!platform.dataFolder().toFile().mkdirs()) {
                platform.logger().error("Failed to create data folder");
                return defaultConfig;
            }
        }

        // Create the config file if it doesn't exist
        if (!configFile.exists()) {
            if (writeConfigFile(configFile, extensionClass, platform, null)) {
                return defaultConfig;
            }
        }

        // Load the config file
        try {
            return new ObjectMapper(new YAMLFactory())
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                    .readValue(configFile, configClass);
        } catch (Exception e) {
            platform.logger().error("Failed to load config (possible update?), loading the default config...");
            return defaultConfig;
        }
    }

    public static void save(BoarPlatform platform, Class<?> extensionClass, Config config) {
        File configFile = platform.dataFolder().resolve("config.yml").toFile();
        // Add missing options if the config is outdated.
        writeConfigFile(configFile, extensionClass, platform, config);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static boolean writeConfigFile(File configFile, Class<?> extensionClass, BoarPlatform platform, Config config) {
        try {
            String s;
            if (config != null && configFile.exists()) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                JsonNode currentNode = mapper.readTree(configFile);
                if (!(currentNode instanceof ObjectNode current)) {
                    throw new IOException("Config root must be a mapping");
                }
                ObjectNode defaults = mapper.valueToTree(config);
                boolean changed = false;
                Iterator<String> fields = defaults.fieldNames();
                while (fields.hasNext()) {
                    String field = fields.next();
                    if (!current.has(field)) {
                        current.set(field, defaults.get(field)); changed = true;}}
                if (!changed) {return true;}
                s = mapper.writeValueAsString(current);
            } else {
                try (FileSystem fileSystem = FileSystems.newFileSystem(new File(extensionClass.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath(), Collections.emptyMap())) {
                    try (InputStream input = Files.newInputStream(fileSystem.getPath("config.yml"))) {
                        byte[] bytes = new byte[input.available()];
                        input.read(bytes);
                        s = new String(bytes);
                    }
                }
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(s.toCharArray());
                writer.flush();
            }
        } catch (IOException | URISyntaxException e) {
            platform.logger().error("Failed to create config", e);
            return false;
        }

        return true;
    }
}

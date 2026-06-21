package ac.boar.anticheat.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTest {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);

    @Test
    void usesDefaultPrefix() {
        assertEquals("§3Boar §7>§r ", Config.DEFAULT_CONFIG.prefix());
        assertEquals("§sBoar §i>§r ", Config.DEFAULT_CONFIG.bedrockPrefix());
    }

    @Test
    void loadsConfiguredPrefix() throws Exception {
        Config config = mapper.readValue("""
                prefix: '&6Boar &8> &r'
                bedrock-prefix: '&eBoar &7> &r'
                """, Config.class);

        assertEquals("§6Boar §8> §r", config.prefix());
        assertEquals("§eBoar §7> §r", config.bedrockPrefix());
    }
}

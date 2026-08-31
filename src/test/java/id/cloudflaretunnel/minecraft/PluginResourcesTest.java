package id.cloudflaretunnel.minecraft;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginResourcesTest {
    @Test
    void pluginMetadataDeclaresExpectedApiAndVersion() throws IOException {
        String pluginYml = readResource("plugin.yml");

        assertTrue(pluginYml.contains("version: 0.1.1"));
        assertTrue(pluginYml.contains("api-version: '1.20'"));
        assertTrue(pluginYml.contains("main: id.cloudflaretunnel.minecraft.CloudflareTunnelPlugin"));
    }

    @Test
    void defaultConfigDoesNotContainARealTunnelToken() throws IOException {
        String config = readResource("config.yml");

        assertTrue(config.contains("PASTE_YOUR_CLOUDFLARE_TUNNEL_TOKEN_HERE"));
        assertFalse(config.contains("eyJ"), "Jangan commit token Cloudflare ke config bawaan.");
    }

    private String readResource(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertTrue(stream != null, "Resource tidak ditemukan: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

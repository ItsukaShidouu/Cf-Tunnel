package id.cloudflaretunnel.minecraft;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

record TunnelSettings(String token, String executable, File workingDirectory, int forceStopAfterSeconds) {
    static TunnelSettings from(CloudflareTunnelPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String token = config.getString("tunnel.token", "").trim();
        String executable = config.getString("tunnel.executable", "cloudflared").trim();
        String configuredDirectory = config.getString("tunnel.working-directory", "").trim();
        File workingDirectory = configuredDirectory.isEmpty()
                ? plugin.getDataFolder()
                : new File(configuredDirectory);
        int forceStopSeconds = Math.max(1, config.getInt("shutdown.force-stop-after-seconds", 10));
        return new TunnelSettings(token, executable, workingDirectory, forceStopSeconds);
    }

    boolean hasUsableToken() {
        return !token.isBlank() && !token.equals("PASTE_YOUR_CLOUDFLARE_TUNNEL_TOKEN_HERE");
    }
}

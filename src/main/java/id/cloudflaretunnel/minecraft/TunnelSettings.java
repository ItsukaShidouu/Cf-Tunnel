package id.cloudflaretunnel.minecraft;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

record TunnelSettings(
        String token,
        String executable,
        File workingDirectory,
        int forceStopAfterSeconds,
        CloudflareApiSettings cloudflareApi
) {
    static TunnelSettings from(CloudflareTunnelPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String token = config.getString("tunnel.token", "").trim();
        String executable = config.getString("tunnel.executable", "cloudflared").trim();
        String configuredDirectory = config.getString("tunnel.working-directory", "").trim();
        File workingDirectory = configuredDirectory.isEmpty()
                ? plugin.getDataFolder()
                : new File(configuredDirectory);
        int forceStopSeconds = Math.max(1, config.getInt("shutdown.force-stop-after-seconds", 10));
        CloudflareApiSettings cloudflareApi = new CloudflareApiSettings(
                config.getBoolean("cloudflare-api.enabled", false),
                config.getString("cloudflare-api.api-token", "").trim(),
                config.getString("cloudflare-api.account-id", "").trim(),
                config.getString("cloudflare-api.zone-id", "").trim(),
                config.getString("cloudflare-api.tunnel-id", "").trim(),
                config.getString("minecraft.hostname", "").trim(),
                config.getString("minecraft.origin-host", "127.0.0.1").trim(),
                config.getInt("minecraft.port", 25565)
        );
        return new TunnelSettings(token, executable, workingDirectory, forceStopSeconds, cloudflareApi);
    }

    boolean hasUsableToken() {
        return !token.isBlank() && !token.equals("PASTE_YOUR_CLOUDFLARE_TUNNEL_TOKEN_HERE");
    }

    record CloudflareApiSettings(
            boolean enabled,
            String apiToken,
            String accountId,
            String zoneId,
            String tunnelId,
            String hostname,
            String originHost,
            int port
    ) {
        String validationError() {
            if (!enabled) {
                return "cloudflare-api.enabled harus bernilai true sebelum provisioning.";
            }
            if (apiToken.isBlank() || apiToken.equals("PASTE_A_LIMITED_CLOUDFLARE_API_TOKEN_HERE")) {
                return "cloudflare-api.api-token belum diisi.";
            }
            if (!accountId.matches("[A-Fa-f0-9]{32}")) {
                return "cloudflare-api.account-id harus berupa Cloudflare Account ID 32 karakter.";
            }
            if (!zoneId.matches("[A-Fa-f0-9]{32}")) {
                return "cloudflare-api.zone-id harus berupa Zone ID 32 karakter.";
            }
            if (!tunnelId.matches("[A-Fa-f0-9-]{36}")) {
                return "cloudflare-api.tunnel-id harus berupa UUID tunnel.";
            }
            if (!hostname.matches("(?i)(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
                return "minecraft.hostname harus berupa hostname lengkap, misalnya mc.example.com.";
            }
            if (originHost.isBlank() || originHost.contains(":")) {
                return "minecraft.origin-host harus berupa hostname atau alamat IPv4 tanpa port.";
            }
            if (port < 1 || port > 65535) {
                return "minecraft.port harus berada di antara 1 dan 65535.";
            }
            return null;
        }

        String serviceUrl() {
            return "tcp://" + originHost + ":" + port;
        }

        String tunnelDnsTarget() {
            return tunnelId + ".cfargotunnel.com";
        }
    }
}

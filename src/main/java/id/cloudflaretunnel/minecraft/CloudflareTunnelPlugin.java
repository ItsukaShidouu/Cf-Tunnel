package id.cloudflaretunnel.minecraft;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class CloudflareTunnelPlugin extends JavaPlugin {
    private TunnelManager tunnelManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.tunnelManager = new TunnelManager(this, TunnelSettings.from(this));

        PluginCommand command = Objects.requireNonNull(getCommand("cftunnel"));
        TunnelCommand executor = new TunnelCommand(this, tunnelManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        if (getConfig().getBoolean("tunnel.auto-start", false)) {
            getLogger().info("Auto-start Cloudflare Tunnel diminta.");
            getServer().getScheduler().runTaskAsynchronously(this, () -> tunnelManager.start());
        }
    }

    @Override
    public void onDisable() {
        if (tunnelManager != null) {
            tunnelManager.shutdown();
        }
    }

    public TunnelManager reloadTunnelSettings() {
        reloadConfig();
        tunnelManager.updateSettings(TunnelSettings.from(this));
        return tunnelManager;
    }
}

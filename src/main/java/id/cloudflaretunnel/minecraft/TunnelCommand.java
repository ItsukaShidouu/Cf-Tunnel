package id.cloudflaretunnel.minecraft;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

final class TunnelCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("status", "start", "stop", "restart", "reload", "guide");

    private final CloudflareTunnelPlugin plugin;
    private final TunnelManager tunnel;

    TunnelCommand(CloudflareTunnelPlugin plugin, TunnelManager tunnel) {
        this.plugin = plugin;
        this.tunnel = tunnel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&e/" + label + " <status|start|stop|restart|reload|guide>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "start" -> runAsync(sender, "start", () -> tunnel.start());
            case "stop" -> runAsync(sender, "stop", () -> tunnel.stop());
            case "restart" -> restart(sender);
            case "reload" -> reload(sender);
            case "guide" -> guide(sender);
            default -> sender.sendMessage(color("&cSubperintah tidak dikenal."));
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(color(tunnel.isRunning()
                ? "&aCloudflare Tunnel: BERJALAN"
                : "&cCloudflare Tunnel: BERHENTI"));
    }

    private void restart(CommandSender sender) {
        runAsync(sender, "restart", tunnel::restart);
    }

    private void reload(CommandSender sender) {
        boolean wasRunning = tunnel.isRunning();
        plugin.reloadTunnelSettings();
        sender.sendMessage(color("&aConfig dimuat ulang."));
        if (wasRunning) {
            runAsync(sender, "restart", tunnel::restart);
        }
    }

    private void guide(CommandSender sender) {
        sender.sendMessage(color("&eCloudflare Zero Trust (TCP) mengharuskan pemain menjalankan:"));
        sender.sendMessage(color("&fcloudflared access tcp --hostname mc.domain-anda.com --url localhost:25565"));
        sender.sendMessage(color("&eLalu pemain masuk ke alamat Minecraft: &flocalhost"));
        sender.sendMessage(color("&7Set public hostname di dashboard ke tcp://localhost:25565."));
    }

    private void runAsync(CommandSender sender, String action, Operation operation) {
        sender.sendMessage(color("&eMemproses " + action + " cloudflared..."));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            TunnelManager.Result result = operation.run();
            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(color(
                    (result.success() ? "&a" : "&c") + result.message())));
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @FunctionalInterface
    private interface Operation {
        TunnelManager.Result run();
    }
}

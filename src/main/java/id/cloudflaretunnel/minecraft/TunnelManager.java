package id.cloudflaretunnel.minecraft;

import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class TunnelManager {
    private static final int MAX_LOG_LINES = 40;

    private final CloudflareTunnelPlugin plugin;
    private final Deque<String> recentLogs = new ArrayDeque<>();
    private volatile TunnelSettings settings;
    private volatile Process process;
    private volatile BukkitTask delayedForceStop;

    TunnelManager(CloudflareTunnelPlugin plugin, TunnelSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    synchronized void updateSettings(TunnelSettings updated) {
        this.settings = updated;
    }

    synchronized Result start() {
        if (isRunning()) {
            return Result.failure("Tunnel sudah berjalan.");
        }
        if (!settings.hasUsableToken()) {
            return Result.failure("tunnel.token belum diisi di plugins/CloudflareTunnel/config.yml.");
        }
        if (settings.executable().isBlank()) {
            return Result.failure("tunnel.executable tidak boleh kosong.");
        }
        if (!settings.workingDirectory().isDirectory()) {
            return Result.failure("Folder working-directory tidak ditemukan: " + settings.workingDirectory());
        }

        List<String> command = new ArrayList<>();
        command.add(settings.executable());
        command.add("tunnel");
        command.add("run");
        command.add("--token");
        command.add(settings.token());

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(settings.workingDirectory());
            builder.redirectErrorStream(true);
            process = builder.start();
            readOutput(process, settings.token());
            return Result.success("cloudflared sedang dimulai.");
        } catch (IOException exception) {
            process = null;
            return Result.failure("Tidak dapat menjalankan cloudflared: " + exception.getMessage()
                    + ". Periksa tunnel.executable dan PATH.");
        }
    }

    synchronized Result stop() {
        if (!isRunning()) {
            process = null;
            return Result.failure("Tunnel tidak sedang berjalan.");
        }
        Process activeProcess = process;
        activeProcess.destroy();
        if (delayedForceStop != null) {
            delayedForceStop.cancel();
        }
        delayedForceStop = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (activeProcess.isAlive()) {
                plugin.getLogger().warning("cloudflared belum berhenti; menghentikan paksa proses milik plugin.");
                activeProcess.destroyForcibly();
            }
        }, settings.forceStopAfterSeconds() * 20L);
        return Result.success("Perintah stop dikirim ke cloudflared.");
    }

    synchronized void shutdown() {
        if (delayedForceStop != null) {
            delayedForceStop.cancel();
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        process = null;
    }

    synchronized Result restart() {
        if (delayedForceStop != null) {
            delayedForceStop.cancel();
            delayedForceStop = null;
        }
        if (isRunning()) {
            Process activeProcess = process;
            activeProcess.destroy();
            try {
                if (!activeProcess.waitFor(5, TimeUnit.SECONDS)) {
                    activeProcess.destroyForcibly();
                    activeProcess.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Result.failure("Restart dibatalkan karena thread terinterupsi.");
            }
            process = null;
        }
        return start();
    }

    boolean isRunning() {
        Process activeProcess = process;
        return activeProcess != null && activeProcess.isAlive();
    }

    List<String> recentLogs() {
        synchronized (recentLogs) {
            return List.copyOf(recentLogs);
        }
    }

    private void readOutput(Process startedProcess, String token) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    addLog(line.replace(token, "[REDACTED]"));
                }
            } catch (IOException exception) {
                if (startedProcess.isAlive()) {
                    plugin.getLogger().warning("Gagal membaca output cloudflared: " + exception.getMessage());
                }
            }
        });
    }

    private void addLog(String line) {
        synchronized (recentLogs) {
            recentLogs.addLast(line);
            while (recentLogs.size() > MAX_LOG_LINES) {
                recentLogs.removeFirst();
            }
        }
        plugin.getLogger().info("[cloudflared] " + line);
    }

    record Result(boolean success, String message) {
        static Result success(String message) {
            return new Result(true, message);
        }

        static Result failure(String message) {
            return new Result(false, message);
        }
    }
}

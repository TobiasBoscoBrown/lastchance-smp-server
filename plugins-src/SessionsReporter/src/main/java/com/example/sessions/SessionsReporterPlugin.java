package com.example.sessions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;

public final class SessionsReporterPlugin extends JavaPlugin implements Listener {
    private HttpClient client;
    private String baseUrl;
    private String apiKey;
    private int heartbeatSeconds;
    private volatile boolean running;
    private final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.baseUrl = getConfig().getString("baseUrl", "http://localhost:3000/api/sessions");
        this.apiKey = getConfig().getString("apiKey", "");
        this.heartbeatSeconds = getConfig().getInt("heartbeatSeconds", 60);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        Bukkit.getPluginManager().registerEvents(this, this);
        this.running = true;
        startHeartbeat();
        getLogger().info("SessionsReporter enabled; baseUrl=" + baseUrl);
    }

    @Override
    public void onDisable() {
        this.running = false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        postJson("join", String.format("{\"playerId\":\"%s\",\"name\":\"%s\"}",
                p.getUniqueId(), escape(p.getName())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        postJson("quit", String.format("{\"playerId\":\"%s\"}", p.getUniqueId()));
        deadPlayers.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        deadPlayers.add(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        deadPlayers.remove(e.getPlayer().getUniqueId());
    }

    private void startHeartbeat() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!running) return;
            try {
                // Collect snapshot of online players to avoid concurrent modification
                Set<UUID> ids = new HashSet<>();
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    UUID id = pl.getUniqueId();
                    ids.add(id);
                }
                for (UUID id : ids) {
                    postHeartbeat(id);
                }
            } catch (Exception ex) {
                getLogger().warning("Heartbeat error: " + ex.getMessage());
            }
        }, 20L * heartbeatSeconds, 20L * heartbeatSeconds);
    }

    private void postHeartbeat(UUID id) {
        String json = String.format("{\"playerId\":\"%s\"}", id);
        String url = baseUrl + "/heartbeat";
        final boolean isDead = deadPlayers.contains(id);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("X-API-Key", apiKey)
                        .header("X-Player-State", isDead ? "dead" : "alive")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    // ok
                } else {
                    getLogger().warning("POST heartbeat failed: status=" + resp.statusCode());
                }
            } catch (Exception ex) {
                getLogger().warning("POST heartbeat failed: " + ex.getMessage());
            }
        });
    }

    private void postJson(String path, String json) {
        if (apiKey == null || apiKey.isEmpty()) {
            getLogger().warning("apiKey is empty; not sending " + path);
            return;
        }
        String url = baseUrl + "/" + path;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("X-API-Key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    // ok
                } else {
                    getLogger().warning("POST " + path + " failed: status=" + resp.statusCode());
                }
            } catch (Exception ex) {
                getLogger().warning("POST " + path + " failed: " + ex.getMessage());
            }
        });
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

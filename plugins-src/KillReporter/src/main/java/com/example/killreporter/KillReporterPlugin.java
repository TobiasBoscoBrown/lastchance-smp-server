package com.example.killreporter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class KillReporterPlugin extends JavaPlugin implements Listener {
    private HttpClient client;
    private String baseUrl;
    private String apiKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.baseUrl = getConfig().getString("api.baseUrl", "http://localhost:3000");
        this.apiKey = getConfig().getString("api.key", "");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        String victimId = victim.getUniqueId().toString();
        String killerId = killer.getUniqueId().toString();
        String killId = UUID.randomUUID().toString();
        String tsIso = Instant.now().toString();
        String json = String.format("{\"killerId\":\"%s\",\"victimId\":\"%s\",\"ts\":\"%s\",\"killId\":\"%s\"}",
                killerId, victimId, tsIso, killId);
        post("/api/kills/record", json);
    }

    private void post(String path, String json) {
        if (apiKey == null || apiKey.isEmpty()) return;
        String url = baseUrl + path;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    getLogger().warning("Kill post failed: " + resp.statusCode());
                }
            } catch (Exception ex) {
                getLogger().warning("Kill post error: " + ex.getMessage());
            }
        });
    }
}

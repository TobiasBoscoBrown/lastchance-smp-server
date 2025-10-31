package com.example.deaths;

import org.bukkit.Bukkit;
import org.bukkit.World;
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
import java.util.UUID;

public final class DeathsReporterPlugin extends JavaPlugin implements Listener {
    private HttpClient client;
    private String apiUrl;
    private String apiKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.apiUrl = getConfig().getString("apiUrl", "http://localhost:3000/api/deaths");
        this.apiKey = getConfig().getString("apiKey", "");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("DeathsReporter enabled; posting to " + this.apiUrl);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        String playerName = event.getEntity().getName();
        String cause = "UNKNOWN";
        if (event.getEntity().getLastDamageCause() != null && event.getEntity().getLastDamageCause().getCause() != null) {
            cause = event.getEntity().getLastDamageCause().getCause().name();
        }
        World w = event.getEntity().getWorld();
        String map = w != null ? w.getName() : "world";

        String json = String.format("{\"playerId\":\"%s\",\"playerName\":\"%s\",\"cause\":\"%s\",\"map\":\"%s\"}",
                playerId, escape(playerName), escape(cause), escape(map));

        if (apiKey == null || apiKey.isEmpty()) {
            getLogger().warning("apiKey is empty; not sending death event");
            return;
        }

        final String payload = json;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("X-API-Key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    getLogger().fine("Death event posted successfully");
                } else {
                    getLogger().warning("Death event post failed: status=" + resp.statusCode());
                }
            } catch (Exception ex) {
                getLogger().warning("Failed to post death event: " + ex.getMessage());
            }
        });
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

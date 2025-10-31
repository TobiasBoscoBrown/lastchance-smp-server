package com.example.downedgate;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public final class DownedGatePlugin extends JavaPlugin implements Listener {
    private HttpClient client;
    private String baseUrl;
    private String apiKey;
    private boolean failOpenOnError;
    private String kickMessage;
    private String pathTemplate;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.baseUrl = getConfig().getString("baseUrl", "http://localhost:3000/api");
        this.apiKey = getConfig().getString("apiKey", "");
        this.failOpenOnError = getConfig().getBoolean("failOpenOnError", true);
        this.kickMessage = getConfig().getString("kickMessage", "You are currently downed. Please wait to be revived.");
        this.pathTemplate = getConfig().getString("pathTemplate", "/player/%s/downed");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DownedGate enabled; checking pattern " + baseUrl + pathTemplate);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        String name = e.getName();
        String dashed = uuid.toString();
        String undashed = dashed.replace("-", "");

        String[] idsToTry = new String[] { dashed, undashed, name };
        boolean anyTried = false;
        for (String idVal : idsToTry) {
            try {
                String url = baseUrl + String.format(pathTemplate, idVal);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/json")
                        .header("X-API-Key", apiKey)
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                anyTried = true;
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    boolean downed = parseDowned(resp.body());
                    if (downed) {
                        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
                    }
                    return; // success (downed or not), stop trying others
                } else if (resp.statusCode() == 404) {
                    // Try next identifier format
                    getLogger().warning("Downed check 404 at URL: " + url + "; trying next identifier");
                    continue;
                } else {
                    getLogger().warning("Downed check non-2xx: status=" + resp.statusCode() + " url=" + url);
                    if (!failOpenOnError) {
                        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Service unavailable. Try again.");
                        return;
                    }
                    // fail-open: allow, but stop trying more
                    return;
                }
            } catch (Exception ex) {
                getLogger().warning("Downed check failed for identifier attempt: " + ex.getMessage());
                if (!failOpenOnError) {
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Service unavailable. Try again.");
                    return;
                }
                // else try next identifier
            }
        }
        // If none succeeded (e.g., all 404) and failOpenOnError is false, optionally block
        if (!failOpenOnError && anyTried) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Service unavailable. Try again.");
        }
    }

    private boolean parseDowned(String json) {
        // Minimal parse without external libs: look for '"downed":true' ignoring whitespace
        if (json == null) return false;
        String s = json.toLowerCase().replaceAll("\\s+", "");
        return s.contains("\"downed\":true");
    }
}

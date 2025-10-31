package com.example.kickondeath;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class KickOnDeathPlugin extends JavaPlugin implements Listener {
    private int seconds;
    private String message;
    private String countdownFormat;
    private final Map<UUID, Integer> tasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.seconds = getConfig().getInt("seconds", 30);
        this.message = getConfig().getString("message", "You died. Kicked after %s seconds.");
        this.countdownFormat = getConfig().getString("countdown", "Kicking in %ds...");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("KickOnDeath enabled; kicking after " + seconds + "s");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        final UUID id = e.getEntity().getUniqueId();
        final String kickMsg = String.format(message, seconds);
        // Start a 1s countdown that updates the death screen via title
        final int[] remaining = new int[] { seconds };
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                cancelTask(id);
                return;
            }
            int sec = remaining[0];
            // Show countdown as subtitle; keep blank title
            try {
                p.sendTitle("", String.format(countdownFormat, sec), 0, 25, 0);
            } catch (Throwable ignored) {
                // ignore title API issues
            }
            if (sec <= 0) {
                cancelTask(id);
                p.kickPlayer(kickMsg);
                return;
            }
            remaining[0] = sec - 1;
        }, 0L, 20L);
        tasks.put(id, taskId);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        // Do not allow entering the world; kick immediately
        Player p = e.getPlayer();
        cancelTask(p.getUniqueId());
        String kickMsg = String.format(message, Math.max(0, seconds));
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline()) p.kickPlayer(kickMsg);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancelTask(e.getPlayer().getUniqueId());
    }

    private void cancelTask(UUID id) {
        Integer taskId = tasks.remove(id);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}

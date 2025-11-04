package com.example.randomspawn;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomSpawnPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("RandomSpawn enabled: respawns are randomized relative to nametag distance");
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent e) {
        if (e.getPlayer() == null) return;
        if (e.getPlayer().hasPlayedBefore()) return;
        World world = getTargetWorld();
        Location loc = findSafeRandom(world);
        if (loc != null) {
            if (getConfig().getBoolean("setSpawnPoint", true)) {
                e.getPlayer().setBedSpawnLocation(loc, true);
            }
            // Teleport next tick to avoid potential joining-state issues
            Bukkit.getScheduler().runTask(this, () -> e.getPlayer().teleportAsync(loc));
        } else {
            getLogger().warning("Failed to find safe random spawn for first join: " + e.getPlayer().getName());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        // Always randomize respawn location
        World world = getTargetWorld();
        Location loc = findSafeRandom(world);
        if (loc != null) {
            if (getConfig().getBoolean("setSpawnPoint", true) && e.getPlayer() != null) {
                e.getPlayer().setBedSpawnLocation(loc, true);
            }
            e.setRespawnLocation(loc);
        } else {
            getLogger().warning("Failed to find safe random respawn; leaving default location");
        }
    }

    private World getTargetWorld() {
        String worldName = getConfig().getString("world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return w;
    }

    private Location findSafeRandom(World world) {
        if (world == null) return null;
        int nametag = Math.max(16, getConfig().getInt("nametagDistance", 64));
        double outerFactor = Math.max(1.0, getConfig().getDouble("outerFactor", 2.0));
        int retries = Math.max(1, getConfig().getInt("retries", 25));
        int minY = getConfig().getInt("minY", 63);
        int safeRadius = Math.max(0, getConfig().getInt("safeRadiusCheck", 2));
        List<String> blacklistWorlds = getConfig().getStringList("blacklistWorlds");
        if (blacklistWorlds != null && blacklistWorlds.contains(world.getName())) return null;

        Set<String> avoidBiomes = new HashSet<>(getConfig().getStringList("avoidBiomes"));
        Set<String> avoidBlocks = new HashSet<>(getConfig().getStringList("avoidBlocks"));

        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < retries; i++) {
            double angle = r.nextDouble(0, Math.PI * 2);

            // Two-bucket radius selection: 50% within nametag, 50% outside up to outerFactor*nametag
            boolean within = r.nextBoolean();
            double a = within ? 0.0 : nametag;
            double b = within ? (double) nametag : nametag * outerFactor;
            double radius = Math.sqrt(r.nextDouble(a * a, b * b));
            int x = (int) Math.round(radius * Math.cos(angle));
            int z = (int) Math.round(radius * Math.sin(angle));

            int cx = x >> 4;
            int cz = z >> 4;
            world.getChunkAtAsync(cx, cz).join();

            Biome biome = world.getBiome(x, world.getMinHeight(), z);
            if (biome != null && avoidBiomes.contains(biome.name())) continue;

            int y = world.getHighestBlockYAt(x, z);
            if (y < minY) continue;
            Location base = new Location(world, x + 0.5, y, z + 0.5);
            if (!isSafe(base, avoidBlocks, safeRadius)) continue;
            return base.add(0, 1, 0);
        }
        return null;
    }

    private boolean isSafe(Location loc, Set<String> avoidBlocks, int radius) {
        World w = loc.getWorld();
        if (w == null) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        // Block at feet and head must be air
        Material feet = w.getBlockAt(bx, by + 1, bz).getType();
        Material head = w.getBlockAt(bx, by + 2, bz).getType();
        if (feet.isSolid() || head.isSolid()) return false;

        // Ground must be solid and not harmful
        Material ground = w.getBlockAt(bx, by, bz).getType();
        if (!ground.isSolid()) return false;
        if (avoidBlocks.contains(ground.name())) return false;

        // Nearby hazard check
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Material t = w.getBlockAt(bx + dx, by + dy, bz + dz).getType();
                    if (avoidBlocks.contains(t.name())) return false;
                    if (t == Material.LAVA || t == Material.WATER || t == Material.FIRE || t == Material.CACTUS || t == Material.MAGMA_BLOCK) return false;
                }
            }
        }
        return true;
    }

}

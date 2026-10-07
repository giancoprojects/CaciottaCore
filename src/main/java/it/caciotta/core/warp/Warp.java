package it.caciotta.core.warp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modello dati per un warp salvato nel server.
 */
public class Warp {

    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final UUID creatorUuid;
    private final String creatorName;
    private final long createdAt;

    public Warp(String name, String worldName, double x, double y, double z, float yaw, float pitch, UUID creatorUuid, String creatorName, long createdAt) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName != null ? creatorName : "Server";
        this.createdAt = createdAt;
    }

    public static Warp fromLocation(String name, Location loc, Player creator) {
        String wName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        UUID uuid = creator != null ? creator.getUniqueId() : null;
        String cName = creator != null ? creator.getName() : "Server";
        return new Warp(name, wName, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), uuid, cName, System.currentTimeMillis());
    }

    public static Warp fromMap(String name, Map<String, Object> map) {
        String world = (String) map.get("world");
        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        double z = ((Number) map.get("z")).doubleValue();
        float yaw = ((Number) map.get("yaw")).floatValue();
        float pitch = ((Number) map.get("pitch")).floatValue();
        String uuidStr = (String) map.get("creatorUuid");
        UUID creatorUuid = null;
        if (uuidStr != null && !uuidStr.isEmpty()) {
            try {
                creatorUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String creatorName = (String) map.getOrDefault("creatorName", "Server");
        long created = map.containsKey("createdAt") ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis();
        return new Warp(name, world, x, y, z, yaw, pitch, creatorUuid, creatorName, created);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", yaw);
        map.put("pitch", pitch);
        if (creatorUuid != null) {
            map.put("creatorUuid", creatorUuid.toString());
        }
        map.put("creatorName", creatorName);
        map.put("createdAt", createdAt);
        return map;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}

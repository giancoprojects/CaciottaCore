package it.caciotta.core.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Modello dati per una home impostata da un giocatore.
 */
public class Home {

    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final long createdAt;

    public Home(String name, String worldName, double x, double y, double z, float yaw, float pitch, long createdAt) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = createdAt;
    }

    public static Home fromLocation(String name, Location loc) {
        String wName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        return new Home(name, wName, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), System.currentTimeMillis());
    }

    public static Home fromMap(String name, Map<String, Object> map) {
        String world = (String) map.get("world");
        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        double z = ((Number) map.get("z")).doubleValue();
        float yaw = ((Number) map.get("yaw")).floatValue();
        float pitch = ((Number) map.get("pitch")).floatValue();
        long created = map.containsKey("createdAt") ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis();
        return new Home(name, world, x, y, z, yaw, pitch, created);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", yaw);
        map.put("pitch", pitch);
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

    public long getCreatedAt() {
        return createdAt;
    }
}
